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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Password reset: request a link, then use it.
 *
 * ORCHESTRATION ONLY, mirroring EmailVerificationService. Token generation,
 * hashing and atomic consumption belong to VerificationTokenService (Phase 2);
 * transport belongs to the `email` package (Phase 3); JWT invalidation happens
 * for free through Phase 5's `cca` claim once credentialsChangedAt moves. This
 * class decides WHEN a reset is warranted and applies the result.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final VerificationTokenService tokenService;
    private final PasswordResetEmailFactory emailFactory;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityProperties properties;

    public PasswordResetService(VerificationTokenService tokenService,
                                PasswordResetEmailFactory emailFactory,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher eventPublisher,
                                IdentityProperties properties) {
        this.tokenService = tokenService;
        this.emailFactory = emailFactory;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Requests a reset link.
     *
     * RETURNS VOID AND NEVER SIGNALS WHETHER THE ACCOUNT EXISTS. Unknown
     * address and deactivated account both fall through to the same silent
     * no-op, so the controller answers an identical 202 in every case.
     * Amplifying account enumeration into an endpoint that also sends mail is
     * the specific thing this avoids.
     *
     * UNVERIFIED ACCOUNTS ARE ALLOWED TO RESET, deliberately. Reaching a reset
     * link proves mailbox control just as well as a verification link does, and
     * refusing would strand a user who registered, never clicked verify, and
     * then forgot their password - with no way out. resetPassword() marks the
     * account verified for exactly that reason.
     *
     * DEACTIVATED ACCOUNTS ARE REFUSED. An admin disabled the account; letting
     * its owner quietly rotate credentials would partially undo a moderation
     * decision, and they still could not log in afterwards.
     *
     * NO ARTIFICIAL DELAY (D12). A real account writes a token row and is
     * therefore measurably slower than an unknown one. Padding every request to
     * hide that would spend latency closing a side channel that registration
     * already leaves wide open with its 409, and the incoherence is worse than
     * the leak.
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
    public void requestReset(String email) {
        String normalised = normalizeEmail(email);

        Optional<User> found = userRepository.findByEmail(normalised);
        if (found.isEmpty()) {
            log.debug("Reset requested for an unknown address; answering generically");
            return;
        }

        User user = found.get();

        if (!user.isActive()) {
            log.debug("Reset requested for a deactivated account {}", user.getId());
            return;
        }

        Duration remaining = tokenService.cooldownRemaining(
                user.getId(), EmailTokenType.RESET_PASSWORD, properties.resendCooldown());

        if (!remaining.isZero()) {
            throw new CooldownActiveException(remaining);
        }

        /*
         * issue() invalidates any live RESET_PASSWORD token for this user before
         * inserting the new one, so requesting a second link kills the first.
         * Two working reset links for one account would double the window in
         * which a leaked mailbox is exploitable.
         */
        String rawToken = tokenService.issue(user, EmailTokenType.RESET_PASSWORD);

        EmailMessage message =
                emailFactory.createResetEmail(user.getEmail(), user.getFullName(), rawToken);

        eventPublisher.publishEvent(new SendEmailRequestedEvent(message));

        // Never the token, never the link, never the address.
        log.debug("Queued password-reset email for user {}", user.getId());
    }

    /**
     * Consumes a reset token and applies the new password.
     *
     * EVERYTHING HERE HAPPENS OR NOTHING DOES - one transaction:
     *
     *   1. consume the token atomically (Phase 2's conditional UPDATE)
     *   2. encode and store the new password hash
     *   3. move credentialsChangedAt forward
     *   4. mark the email verified
     *   5. invalidate every remaining live token of BOTH types
     *   6. publish the "password changed" notification
     *
     * A rollback therefore leaves the password unchanged, the token unconsumed,
     * and - because the listener is bound to AFTER_COMMIT - no email sent.
     *
     * STEP 3 IS THE SECURITY PAYLOAD. Moving credentialsChangedAt makes every
     * JWT issued before this moment fail Phase 5's `cca` check on its very next
     * request. Without it the whole flow would be theatre: an attacker holding
     * a stolen token would keep full access for up to 24 hours after the victim
     * "secured" their account.
     *
     * STEP 5 covers the tokens the consume did not touch: a still-live
     * VERIFY_EMAIL link, and any other RESET_PASSWORD row. A reset link sitting
     * in a compromised mailbox must not outlive the reset it authorised.
     *
     * NO JWT IS ISSUED (D9). Auto-login after a flow whose premise is "we are
     * not certain who you are" is the wrong instinct.
     *
     * Failure modes come from consume(): TokenInvalidException,
     * TokenExpiredException and TokenAlreadyUsedException, already mapped by
     * GlobalExceptionHandler to 400 / 410 / 409. Each throws before any write,
     * so an invalid token cannot change a password.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = tokenService.consume(rawToken, EmailTokenType.RESET_PASSWORD);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialsChangedAt(Instant.now());

        // Reaching this link proved mailbox control, so an unverified account
        // is now verified - otherwise a user who reset without ever verifying
        // would be stuck in a loop.
        user.setEmailVerified(true);

        userRepository.save(user);

        tokenService.invalidateAll(user.getId(), EmailTokenType.RESET_PASSWORD);
        tokenService.invalidateAll(user.getId(), EmailTokenType.VERIFY_EMAIL);

        EmailMessage notification =
                emailFactory.createPasswordChangedEmail(user.getEmail(), user.getFullName());
        eventPublisher.publishEvent(new SendEmailRequestedEvent(notification));

        // Id only. Never the token, the password, or the address.
        log.info("Password reset completed for user {}; all prior sessions invalidated",
                user.getId());
    }

    /** Same rule as AuthServiceImpl: the column only ever holds trimmed lowercase. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
