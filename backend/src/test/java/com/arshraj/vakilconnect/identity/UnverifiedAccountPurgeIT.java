package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.service.UnverifiedAccountPurgeJob;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.support.EmailCaptureConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UnverifiedAccountPurgeJob — deletes rows from `users`, so every guard is
 * pinned here.
 *
 * Distinct from EmailTokenPurgeJob, which only removes token rows. The blast
 * radius of this job is an order of magnitude larger, which is why the tests
 * assert the REFUSALS at least as hard as the deletions.
 */
@DisplayName("Unverified account purge")
@Import(EmailCaptureConfig.class)
class UnverifiedAccountPurgeIT extends AbstractIntegrationTest {

    @Autowired
    private UnverifiedAccountPurgeJob purgeJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailCaptureConfig.RecordingEmailSender mailbox;

    @PersistenceContext
    private EntityManager entityManager;

    private String registerClient(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());
        return email;
    }

    /** See SquatTakeoverIT#age — created_at is updatable=false by design. */
    private void age(String email, int days) {
        jdbcTemplate.update(
                "UPDATE users SET created_at = ? WHERE email = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(days)), email);
        entityManager.clear();
    }

    private boolean exists(String email) {
        return userRepositoryForSupport.findByEmail(email).isPresent();
    }

    @Test
    @DisplayName("an abandoned unverified CLIENT older than the retention is deleted")
    void purgesAbandonedAccount() throws Exception {
        String email = registerClient("purgeme");
        age(email, 60);

        purgeJob.purgeEligible();
        entityManager.clear();

        assertFalse(exists(email), "an abandoned unverified account should be purged");
    }

    @Test
    @DisplayName("a VERIFIED account is never purged, however old")
    void keepsVerifiedAccounts() throws Exception {
        String email = registerClient("purgeverified");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", mailbox.lastToken()))))
                .andExpect(status().isOk());

        age(email, 400);

        purgeJob.purgeEligible();
        entityManager.clear();

        assertTrue(exists(email), "a verified account is somebody's, permanently");
    }

    @Test
    @DisplayName("a RECENT unverified account is never purged")
    void keepsRecentAccounts() throws Exception {
        String email = registerClient("purgerecent");
        age(email, 5);

        purgeJob.purgeEligible();
        entityManager.clear();

        assertTrue(exists(email), "retention is P30D; five days is far inside it");
    }

    @Test
    @DisplayName("a LAWYER is never purged — the lawyers FK has no cascade")
    void keepsLawyers() throws Exception {
        String email = uniqueEmail("purgelawyer");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(lawyerRegistration(email))))
                .andExpect(status().isCreated());

        age(email, 400);

        // Excluded in the QUERY, not filtered afterwards: deleting a user with a
        // lawyers row would fail outright on the foreign key.
        purgeJob.purgeEligible();
        entityManager.clear();

        assertTrue(exists(email));
    }

    @Test
    @DisplayName("an ADMIN is never purged")
    void keepsAdmins() throws Exception {
        String email = uniqueEmail("purgeadmin");
        registerAndLoginAdmin(email);

        age(email, 400);

        // The bootstrap admin is unverified in some deployments and is the only
        // way into the system. An automated job must never be able to reach it.
        purgeJob.purgeEligible();
        entityManager.clear();

        assertTrue(exists(email));
    }

    @Test
    @DisplayName("the purge returns a count and is safely repeatable")
    void isIdempotent() throws Exception {
        String email = registerClient("purgetwice");
        age(email, 60);

        purgeJob.purgeEligible();
        entityManager.clear();
        assertFalse(exists(email));

        // Running again must be a harmless no-op, since @Scheduled fires on
        // every instance and the job has no leader election.
        purgeJob.purgeEligible();
        entityManager.clear();
        assertFalse(exists(email));
    }
}
