package com.arshraj.vakilconnect.auth;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.support.EmailCaptureConfig;
import com.arshraj.vakilconnect.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7: the email-verification login gate, ENFORCED.
 *
 * Runs in its own context with
 * {@code vakilconnect.identity.verification-enforced=true}. Every other test
 * class keeps the shipped default of false, so this class proves the gate works
 * without changing behaviour for the rest of the suite - which is also how the
 * flag behaves in production.
 *
 * The failure modes worth guarding are not "does it block" but "does it block
 * the RIGHT people": a gate that locks out admins, grandfathered users or
 * verified lawyers is far worse than no gate at all.
 */
@DisplayName("Login verification gate (enforced)")
@Import(EmailCaptureConfig.class)
@TestPropertySource(properties = "vakilconnect.identity.verification-enforced=true")
class LoginVerificationGateIT extends AbstractIntegrationTest {

    @Autowired
    private EmailCaptureConfig.RecordingEmailSender mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.reset();
    }

    private String registerClient(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());
        return email;
    }

    private void verifyVia(String rawToken) throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> credentials(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private int loginStatus(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(credentials(email, password))))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------- blocked

    @Test
    @DisplayName("an UNVERIFIED account is refused with 403 EMAIL_NOT_VERIFIED")
    void unverifiedIsBlocked() throws Exception {
        String email = registerClient("gateunverified");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(credentials(email, DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("403, not 401 — the password was correct")
    void refusalIsNotACredentialFailure() throws Exception {
        String email = registerClient("gatestatus");

        /*
         * A 401 here would tell the user their password was wrong and send them
         * to reset a password that works perfectly well. 403 says "we know who
         * you are, you may not proceed yet", which is the actual situation.
         */
        org.junit.jupiter.api.Assertions.assertEquals(
                403, loginStatus(email, DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("a WRONG password is still 401, even on an unverified account")
    void wrongPasswordStill401() throws Exception {
        String email = registerClient("gatewrongpw");

        // The gate runs AFTER authentication. A prober must not be able to use
        // it to discover which addresses exist without knowing a password.
        org.junit.jupiter.api.Assertions.assertEquals(
                401, loginStatus(email, "definitely-not-the-password"));
    }

    // ------------------------------------------------------------- allowed

    @Test
    @DisplayName("verifying then logging in works")
    void verifyThenLogin() throws Exception {
        String email = registerClient("gateverify");
        verifyVia(mailbox.lastToken());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(credentials(email, DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("a GRANDFATHERED account logs in — V7 backfilled them verified")
    void grandfatheredUserCanLogIn() throws Exception {
        String email = registerClient("gategrandfathered");

        // V7 set is_email_verified = true for every pre-existing row. Simulate
        // such an account: verified, but never through the token flow.
        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepositoryForSupport.saveAndFlush(user);

        org.junit.jupiter.api.Assertions.assertEquals(
                200, loginStatus(email, DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("a LAWYER logs in once verified")
    void lawyerCanLogInAfterVerifying() throws Exception {
        String email = uniqueEmail("gatelawyer");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(lawyerRegistration(email))))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(
                403, loginStatus(email, DEFAULT_PASSWORD));

        verifyVia(mailbox.lastToken());

        org.junit.jupiter.api.Assertions.assertEquals(
                200, loginStatus(email, DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("an ADMIN logs in — bootstrap creates admins already verified")
    void adminCanLogIn() throws Exception {
        /*
         * AdminBootstrapRunner sets emailVerified = true because the bootstrap
         * admin has no mailbox to verify from and is the only way into the
         * system. If the gate ever locked admins out, recovery would require
         * direct database access.
         */
        String token = registerAndLoginAdmin(uniqueEmail("gateadmin"));
        org.junit.jupiter.api.Assertions.assertNotNull(token);
        org.junit.jupiter.api.Assertions.assertFalse(token.isBlank());
    }

    // ------------------------------------------------- other states unchanged

    @Test
    @DisplayName("a DEACTIVATED account is still 401, not the verification 403")
    void deactivatedStillBlocked() throws Exception {
        String email = registerClient("gatedeactivated");
        verifyVia(mailbox.lastToken());

        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepositoryForSupport.saveAndFlush(user);

        /*
         * Deactivation fails earlier, inside DaoAuthenticationProvider. Keeping
         * the two distinct is what lets the frontend show a resend button for
         * one and a support link for the other - and proves the gate did not
         * accidentally replace `active` with `emailVerified`.
         */
        org.junit.jupiter.api.Assertions.assertEquals(
                401, loginStatus(email, DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("resetting a password verifies the account, so login then works")
    void passwordResetUnblocksLogin() throws Exception {
        String email = registerClient("gatereset");

        // Clear the shared cooldown started by the registration email.
        var verifyToken = mailbox.lastToken();
        var stored = emailTokenRepositoryForGate.findByTokenHashWithUser(
                tokenHasherForGate.hash(verifyToken)).orElseThrow();
        stored.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(5)));
        emailTokenRepositoryForGate.saveAndFlush(stored);
        mailbox.reset();

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", mailbox.lastToken(),
                                "newPassword", "a-brand-new-password"))))
                .andExpect(status().isOk());

        // Reaching the reset link proved mailbox control, so the account is now
        // verified and the gate lets it through.
        org.junit.jupiter.api.Assertions.assertEquals(
                200, loginStatus(email, "a-brand-new-password"));
    }

    @Autowired
    private com.arshraj.vakilconnect.identity.repository.EmailTokenRepository
            emailTokenRepositoryForGate;

    @Autowired
    private com.arshraj.vakilconnect.identity.service.TokenHasher tokenHasherForGate;
}
