package com.arshraj.vakilconnect.identity.service;

import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import com.arshraj.vakilconnect.review.repository.ReviewRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Deletes abandoned unverified accounts.
 *
 * NOT THE SAME JOB AS EmailTokenPurgeJob. That one removes terminal rows from
 * `email_tokens`; this one removes rows from `users`. The blast radius is
 * completely different, which is why the guards below are deliberately
 * paranoid and each deletion is decided per-row rather than by one bulk
 * DELETE.
 *
 * WHY IT EXISTS. Registration does not require verification, so an address can
 * be occupied by an account nobody ever confirmed. Takeover (AuthServiceImpl)
 * handles the case where the real owner comes back; this handles the long tail
 * nobody ever returns to.
 *
 * FOUR GUARDS, ALL REQUIRED. A row is deleted only when every one holds:
 *
 *   1. NOT VERIFIED. A verified account is somebody's, permanently.
 *   2. ROLE = CLIENT. A LAWYER owns a `lawyers` row whose FK has no ON DELETE
 *      CASCADE, so deleting the user would fail outright - and an ADMIN must
 *      never be reachable by an automated job at all.
 *   3. OLDER THAN unverifiedPurgeAfter (P30D, and necessarily longer than the
 *      7-day takeover window).
 *   4. NO DEPENDENT ROWS - zero appointments and zero reviews. Any activity
 *      means a real person used this account, verified or not.
 *
 * `email_tokens` rows follow via ON DELETE CASCADE (V7), which is the only
 * cascade in play and is intended.
 *
 * SINGLE-INSTANCE ASSUMPTION, same as EmailTokenPurgeJob: @Scheduled fires on
 * every instance. The deletes are idempotent, but disable this on all but one
 * replica via `vakilconnect.identity.purge-enabled` until there is a leader
 * election.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.identity.purge-enabled",
        havingValue = "true", matchIfMissing = true)
public class UnverifiedAccountPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountPurgeJob.class);

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final IdentityProperties properties;

    public UnverifiedAccountPurgeJob(UserRepository userRepository,
                                     AppointmentRepository appointmentRepository,
                                     ReviewRepository reviewRepository,
                                     IdentityProperties properties) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
        this.properties = properties;
    }

    /**
     * 04:15 UTC daily - after EmailTokenPurgeJob at 03:30, so token housekeeping
     * has already run and this job is not competing with it for locks on the
     * same rows.
     *
     * Failures are caught and logged: an uncaught exception from a @Scheduled
     * method means the job silently does not run, and housekeeping falling over
     * must never look like a healthy system.
     */
    @Scheduled(cron = "0 15 4 * * *", zone = "UTC")
    public void purge() {
        try {
            purgeEligible();
        } catch (RuntimeException e) {
            log.error("Unverified account purge failed; will retry on the next schedule", e);
        }
    }

    /**
     * Separated from the schedule so it is callable directly from a test
     * without waiting for a cron.
     *
     * ROW-BY-ROW, NOT A BULK DELETE. A bulk `DELETE ... WHERE` could not
     * express the dependent-row checks without a correlated subquery per table,
     * and getting that wrong deletes real users' accounts. At the volumes this
     * job sees - abandoned signups older than a month - the cost of being
     * explicit is irrelevant.
     *
     * @return how many accounts were removed
     */
    @Transactional
    public int purgeEligible() {
        Instant cutoffInstant = Instant.now().minus(properties.unverifiedPurgeAfter());

        // users.created_at is timestamp(6) mapped as LocalDateTime by
        // BaseEntity, so the cutoff is converted rather than compared across
        // types. UTC because that is what the JVM writes there.
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffInstant, ZoneOffset.UTC);

        List<User> candidates = userRepository
                .findByEmailVerifiedFalseAndRoleAndCreatedAtBefore(Role.CLIENT, cutoff);

        int deleted = 0;

        for (User candidate : candidates) {
            if (appointmentRepository.countByClient(candidate) > 0
                    || reviewRepository.countByClient(candidate) > 0) {
                // Somebody really used this account. Not ours to delete.
                continue;
            }

            userRepository.delete(candidate);
            deleted++;
        }

        if (deleted > 0) {
            log.info("Purged {} unverified account(s) created before {}", deleted, cutoff);
        }
        return deleted;
    }
}
