package com.arshraj.vakilconnect.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily housekeeping for {@code email_tokens}.
 *
 * A thin scheduler, deliberately. The deletion and its transaction live in
 * VerificationTokenService, so the behaviour is testable by calling the service
 * directly rather than by waiting for a cron to fire.
 *
 * SINGLE-INSTANCE ASSUMPTION. @Scheduled fires on EVERY instance this bean is
 * active on, so with two replicas the job runs twice. The deletes are
 * idempotent so nothing breaks, but it is wasted work - disable it on all but
 * one instance via `vakilconnect.identity.purge-enabled` until there is a
 * leader election. This is the same assumption the rate limiter will make.
 *
 * PHASE SCOPE: tokens only. `unverified-purge-after` targets abandoned ACCOUNTS
 * and belongs with the flow that creates them; deleting user rows from here
 * would be a far larger blast radius than this job's name suggests.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.identity.purge-enabled",
        havingValue = "true", matchIfMissing = true)
public class EmailTokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(EmailTokenPurgeJob.class);

    private final VerificationTokenService verificationTokenService;

    public EmailTokenPurgeJob(VerificationTokenService verificationTokenService) {
        this.verificationTokenService = verificationTokenService;
    }

    /**
     * 03:30 UTC daily - off-peak for an India-facing product, and a fixed zone
     * so the schedule does not shift with the host's locale.
     *
     * Failures are caught and logged rather than thrown: an uncaught exception
     * from a @Scheduled method is logged by Spring but the job simply does not
     * run, and housekeeping falling over must never look like a healthy system.
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void purge() {
        try {
            verificationTokenService.purgeExpired();
        } catch (RuntimeException e) {
            log.error("Email token purge failed; will retry on the next schedule", e);
        }
    }
}
