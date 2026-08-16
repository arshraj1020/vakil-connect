package com.arshraj.vakilconnect.identity.service;

import com.arshraj.vakilconnect.common.exception.TokenAlreadyUsedException;
import com.arshraj.vakilconnect.common.exception.TokenExpiredException;
import com.arshraj.vakilconnect.common.exception.TokenInvalidException;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import com.arshraj.vakilconnect.identity.entity.EmailToken;
import com.arshraj.vakilconnect.identity.entity.EmailTokenType;
import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The shared token primitive. Issue, consume, invalidate, purge.
 *
 * DELIBERATELY KNOWS NOTHING ABOUT EMAIL, and nothing about what a token means.
 * Verification and password reset are two callers of the same mechanism, so
 * "reuse the same infrastructure" is structural rather than a convention
 * somebody has to remember. This class is also the only place that touches
 * `email_tokens`.
 *
 * NOTHING CALLS IT YET. That is the point of shipping it alone: the concurrency
 * behaviour can be proven before any user-facing flow depends on it.
 */
@Service
public class VerificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(VerificationTokenService.class);

    private final EmailTokenRepository emailTokenRepository;
    private final TokenHasher tokenHasher;
    private final IdentityProperties properties;

    public VerificationTokenService(EmailTokenRepository emailTokenRepository,
                                    TokenHasher tokenHasher,
                                    IdentityProperties properties) {
        this.emailTokenRepository = emailTokenRepository;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
    }

    /**
     * Issues a token, superseding any live token of the same type for this user.
     *
     * RETURNS THE RAW TOKEN, which is the only time it exists outside the user's
     * inbox. The caller must put it in an email and must not log, persist or
     * return it in an HTTP response.
     *
     * ORDER MATTERS. The previous live token is invalidated FIRST, because
     * `uq_email_tokens_live` permits at most one row per (user, type) with both
     * terminal columns null. Inserting before invalidating would violate it.
     *
     * A CONCURRENT SECOND ISSUE STILL LOSES, and that is intended. Two requests
     * racing here both invalidate, then both insert, and the partial unique
     * index rejects one with a DataIntegrityViolationException. That exception
     * is NOT caught here: catching a constraint violation would leave this
     * transaction aborted and unusable for any further statement. It propagates
     * to GlobalExceptionHandler, which answers 409. The database stays the final
     * authority on concurrent inserts.
     */
    @Transactional
    public String issue(User user, EmailTokenType type) {
        Instant now = Instant.now();

        emailTokenRepository.invalidateLive(user.getId(), type, now);

        String rawToken = tokenHasher.generateRawToken();

        EmailToken token = new EmailToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(tokenHasher.hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttlFor(type)));

        emailTokenRepository.save(token);

        // Id, not hash, and never the raw value.
        log.debug("Issued {} token {}", type, token.getId());

        return rawToken;
    }

    /**
     * Consumes a token and returns its owner, or throws a typed failure.
     *
     * THE ROW COUNT IS THE DECISION. A single conditional UPDATE is both the
     * check and the write, so two concurrent requests presenting the same token
     * cannot both succeed - PostgreSQL serialises them on the row lock and
     * exactly one sees `used_at IS NULL`.
     *
     * Only when that UPDATE matches nothing do we read the row, and then ONLY to
     * decide which error to report. That read can never turn a loss into a win.
     */
    @Transactional
    public User consume(String rawToken, EmailTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new TokenInvalidException();
        }

        Instant now = Instant.now();
        String hash = tokenHasher.hash(rawToken);

        int consumed = emailTokenRepository.consume(hash, type, now);

        if (consumed == 1) {
            // Re-read purely to return the owner. The win is already banked.
            EmailToken token = emailTokenRepository.findByTokenHashWithUser(hash)
                    .orElseThrow(TokenInvalidException::new);
            log.debug("Consumed {} token {}", type, token.getId());
            return token.getUser();
        }

        throw classifyFailure(hash, type, now);
    }

    /**
     * Works out WHY the conditional UPDATE matched nothing.
     *
     * Order is deliberate: the checks run from least to most informative, so a
     * row that is both superseded and expired reports the fact the user can act
     * on. Type mismatch collapses into TOKEN_INVALID rather than admitting that
     * the value exists for a different flow.
     */
    private RuntimeException classifyFailure(String hash, EmailTokenType type, Instant now) {
        EmailToken token = emailTokenRepository.findByTokenHashWithUser(hash).orElse(null);

        if (token == null || token.getType() != type) {
            return new TokenInvalidException();
        }
        if (token.getInvalidatedAt() != null) {
            return new TokenInvalidException();
        }
        if (token.getUsedAt() != null) {
            return new TokenAlreadyUsedException();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            return new TokenExpiredException();
        }

        /*
         * Unreachable in practice: the row matched none of the four conditions
         * the UPDATE tests, yet the UPDATE matched nothing. Reaching here means
         * the query and this method have drifted apart. Fail closed and say so.
         */
        log.warn("Token {} failed consumption but matched no failure condition", token.getId());
        return new TokenInvalidException();
    }

    /**
     * Supersedes every live token of one type for a user, without issuing a
     * replacement. Returns how many were affected.
     *
     * Nothing calls this yet; it is the primitive a password change will use to
     * kill outstanding reset links.
     */
    @Transactional
    public int invalidateAll(UUID userId, EmailTokenType type) {
        return emailTokenRepository.invalidateLive(userId, type, Instant.now());
    }

    /**
     * Deletes tokens that have been terminal for longer than the configured
     * retention. Live tokens are never touched, whatever their age.
     */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.tokenPurgeRetention());
        int deleted = emailTokenRepository.deleteTerminalBefore(cutoff);

        if (deleted > 0) {
            log.info("Purged {} email token(s) terminal before {}", deleted, cutoff);
        }
        return deleted;
    }

    private Duration ttlFor(EmailTokenType type) {
        return switch (type) {
            case VERIFY_EMAIL -> properties.verificationTokenTtl();
            case RESET_PASSWORD -> properties.resetTokenTtl();
        };
    }
}
