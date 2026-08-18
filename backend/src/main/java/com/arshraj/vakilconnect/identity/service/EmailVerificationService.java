package com.arshraj.vakilconnect.identity.service;

import com.arshraj.vakilconnect.common.exception.CooldownActiveException;
import com.arshraj.vakilconnect.email.EmailMessage;
import com.arshraj.vakilconnect.email.event.SendEmailRequestedEvent;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import com.arshraj.vakilconnect.identity.entity.EmailTokenType;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * The verification flow: verify a token, resend a link, and issue the first
 * link at registration.
 *
 * ORCHESTRATION ONLY. Token generation and hashing belong to
 * VerificationTokenService (Phase 2); transport belongs to the `email` package
 * (Phase 3). This class decides WHEN a verification email is warranted and WHO
 * it is for - nothing else.
 *
 * EVERY PUBLISHING PATH IS @Transactional. The Phase 3 listener is bound to
 * AFTER_COMMIT, and a @TransactionalEventListener outside a transaction drops
 * the event silently. Publishing here always happens inside a transaction, so
 * a rollback means no email - which is the entire point of the arrangement.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final VerificationTokenService tokenService;
    private final VerificationEmailFactory emailFactory;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityProperties properties;

    public EmailVerificationService(VerificationTokenService tokenService,
                                    VerificationEmailFactory emailFactory,
                                    UserRepository userRepository,
                                    ApplicationEventPublisher eventPublisher,
                                    IdentityProperties properties) {
        this.tokenService = tokenService;
        this.emailFactory = emailFactory;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Issues a verification token for a freshly created or taken-over account
     * and queues the email.
     *
     * MUST BE CALLED FROM INSIDE THE CALLER'S TRANSACTION. Propagation.MANDATORY
     * makes that a startup-visible contract rather than a convention: calling it
     * without a transaction throws immediately instead of quietly issuing a
     * token whose email is then dropped by the AFTER_COMMIT listener.
     *
     * The raw token exists only as a local variable and inside the rendered
     * message. It is never logged and never returned.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void sendVerificationEmail(User user) {
        String rawToken = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

        EmailMessage message =
                emailFactory.create(user.getEmail(), user.getFullName(), rawToken);

        eventPublisher.publishEvent(new SendEmailRequestedEvent(message));

        // Never the token, never the link, never the address.
        log.debug("Queued verification email for user {}", user.getId());
    }

    /**
     * Consumes a verification token and marks the account verified.
     *
     * Delegates the whole single-use guarantee to Phase 2's atomic conditional
     * UPDATE - there is no token lookup, hashing or state inspection here.
     * consume() throws TokenInvalidException / TokenExpiredException /
     * TokenAlreadyUsedException, which GlobalExceptionHandler already maps to
     * 400 / 410 / 409 with machine-readable codes.
     *
     * IDEMPOTENT ONLY IN THE USEFUL DIRECTION. A second POST of the same token
     * is a 409, not a 200: the token really was consumed already. The FRONTEND
     * renders that as success when the account is verified, because the user's
     * question is "did it work", not "was mine the first request". Answering 200
     * here instead would mean a genuinely replayed token was indistinguishable
     * from a first use.
     */
    @Transactional
    public void verify(String rawToken) {
        User user = tokenService.consume(rawToken, EmailTokenType.VERIFY_EMAIL);

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        log.info("Email verified for user {}", user.getId());
    }

    /**
     * Re-sends a verification link.
     *
     * RETURNS VOID AND NEVER SIGNALS WHETHER THE ACCOUNT EXISTS. Unknown
     * address, already-verified account and deactivated account all fall
     * through to the same silent no-op, so the controller answers an identical
     * 202 in every case. Amplifying account enumeration into an endpoint that
     * also sends mail is the specific thing this avoids.
     *
     * THE ONE OBSERVABLE DIFFERENCE is the cooldown 429, which is reachable
     * only for an existing unverified account. That narrow leak is accepted
     * deliberately - see the note in the Phase 4 report - and is consistent
     * with registration already answering 409 for a known address.
     *
     * TRANSACTION SHAPE. One transaction. The cooldown check runs before any
     * write, so throwing rolls back nothing. If two requests both pass the
     * check, issue() collides on `uq_email_tokens_live` and the resulting
     * DataIntegrityViolationException PROPAGATES uncaught - answered 409 by the
     * global handler. It is never caught here: catching a constraint violation
     * would leave this transaction aborted and every later statement in it
     * would fail.
     */
    @Transactional
    public void resend(String email) {
        String normalised = normalizeEmail(email);

        Optional<User> found = userRepository.findByEmail(normalised);
        if (found.isEmpty()) {
            log.debug("Resend requested for an unknown address; answering generically");
            return;
        }

        User user = found.get();

        if (user.isEmailVerified() || !user.isActive()) {
            log.debug("Resend requested for a verified or inactive account {}", user.getId());
            return;
        }

        Duration remaining = tokenService.cooldownRemaining(
                user.getId(), EmailTokenType.VERIFY_EMAIL, properties.resendCooldown());

        if (!remaining.isZero()) {
            throw new CooldownActiveException(remaining);
        }

        sendVerificationEmail(user);
    }

    /** Same rule as AuthServiceImpl: the column only ever holds trimmed lowercase. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
