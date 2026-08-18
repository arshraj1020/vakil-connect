package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.identity.service.TokenHasher;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.support.EmailCaptureConfig;
import com.arshraj.vakilconnect.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4: registration issues a verification token and queues its email;
 * POST /api/auth/verify-email consumes it.
 *
 * End-to-end through HTTP against the real schema, with email captured rather
 * than sent.
 */
@DisplayName("Email verification flow")
@Import(EmailCaptureConfig.class)
class EmailVerificationIT extends AbstractIntegrationTest {

    @Autowired
    private EmailCaptureConfig.RecordingEmailSender mailbox;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @BeforeEach
    void clearMailbox() {
        mailbox.reset();
    }

    private String verify(String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        return json(body);
    }

    // ---------------------------------------------------------- registration

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("a new CLIENT is created UNVERIFIED and is emailed a token")
        void clientRegistrationIssuesToken() throws Exception {
            String email = uniqueEmail("verifyclient");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
            assertFalse(user.isEmailVerified(), "a new account must start unverified");

            assertEquals(1, mailbox.count(), "exactly one verification email");
            assertEquals("verification", mailbox.last().tag());
        }

        @Test
        @DisplayName("a new LAWYER is also created UNVERIFIED and emailed")
        void lawyerRegistrationIssuesToken() throws Exception {
            String email = uniqueEmail("verifylawyer");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(lawyerRegistration(email))))
                    .andExpect(status().isCreated());

            assertFalse(userRepositoryForSupport.findByEmail(email).orElseThrow().isEmailVerified());
            assertEquals(1, mailbox.count());
        }

        @Test
        @DisplayName("only the HASH is persisted — the emailed token is not in the database")
        void rawTokenIsNeverPersisted() throws Exception {
            String email = uniqueEmail("rawtoken");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            String rawToken = mailbox.lastToken();

            // Searching by the RAW value must find nothing; searching by its
            // HMAC must find the row. If the raw token were ever stored, a
            // database dump would be a set of working links.
            assertTrue(emailTokenRepository.findByTokenHashWithUser(rawToken).isEmpty(),
                    "the raw token must not be a stored hash");
            assertTrue(emailTokenRepository
                            .findByTokenHashWithUser(tokenHasher.hash(rawToken)).isPresent(),
                    "the hash of the emailed token must be stored");
        }

        @Test
        @DisplayName("a failed registration sends no email")
        void failedRegistrationSendsNothing() throws Exception {
            // A LAWYER registration missing its profile is rejected before
            // anything is written, so the transaction never commits and the
            // AFTER_COMMIT listener never fires.
            Map<String, Object> body = clientRegistration(uniqueEmail("nolawyerprofile"));
            body.put("role", "LAWYER");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, mailbox.count(), "a rolled-back registration must not send email");
        }

        @Test
        @DisplayName("ADMIN registration is still rejected and sends nothing")
        void adminRegistrationStillRejected() throws Exception {
            Map<String, Object> body = clientRegistration(uniqueEmail("adminattempt"));
            body.put("role", "ADMIN");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, mailbox.count());
        }
    }

    // ------------------------------------------------------------ verifying

    @Nested
    @DisplayName("POST /api/auth/verify-email")
    class Verify {

        private String registerAndCaptureToken(String prefix) throws Exception {
            String email = uniqueEmail(prefix);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());
            return mailbox.lastToken();
        }

        @Test
        @DisplayName("a valid token verifies the account")
        void validTokenVerifies() throws Exception {
            String email = uniqueEmail("happyverify");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify(mailbox.lastToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verified").value(true));

            assertTrue(userRepositoryForSupport.findByEmail(email).orElseThrow().isEmailVerified());
        }

        @Test
        @DisplayName("verification requires no JWT")
        void requiresNoAuthentication() throws Exception {
            String token = registerAndCaptureToken("noauth");

            // Deliberately no Authorization header: a user who cannot log in
            // yet must still be able to verify.
            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify(token)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("an unknown token is 400 TOKEN_INVALID")
        void unknownToken() throws Exception {
            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify("ThisTokenWasNeverIssuedAnywhereAtAll1234")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }

        @Test
        @DisplayName("a blank token is rejected by validation")
        void blankToken() throws Exception {
            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify("")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("reusing a token is 409 TOKEN_ALREADY_USED")
        void reuseIsRejected() throws Exception {
            String token = registerAndCaptureToken("reuse");

            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify(token)))
                    .andExpect(status().isOk());

            // Single-use is enforced by Phase 2's atomic conditional UPDATE.
            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TOKEN_ALREADY_USED"));
        }

        @Test
        @DisplayName("an expired token is 410 TOKEN_EXPIRED")
        void expiredToken() throws Exception {
            String token = registerAndCaptureToken("expired");

            // Backdate the row rather than sleep: expiry is evaluated in the
            // database, so moving the row's expiry is the honest way to test it.
            var stored = emailTokenRepository
                    .findByTokenHashWithUser(tokenHasher.hash(token)).orElseThrow();
            stored.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            emailTokenRepository.saveAndFlush(stored);

            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify(token)))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("a superseded token is 400 TOKEN_INVALID, not ALREADY_USED")
        void supersededToken() throws Exception {
            String email = uniqueEmail("superseded");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());
            String firstToken = mailbox.lastToken();

            /*
             * Get past the resend cooldown FIRST.
             *
             * Registration has just issued a token, so the 60-second window is
             * running and an immediate resend is correctly refused with 429 -
             * see ResendVerificationIT#cooldownEnforced, which asserts exactly
             * that. This test is about SUPERSESSION, which is only reachable
             * through a successful resend, so it has to clear the cooldown
             * legitimately rather than assert its way around it.
             *
             * created_at is the cooldown's source of truth, so backdating the
             * row is equivalent to waiting - and does not add a minute to the
             * suite. Unlike users.created_at (updatable = false), this column is
             * an ordinary mapped field with a setter.
             */
            var issued = emailTokenRepository
                    .findByTokenHashWithUser(tokenHasher.hash(firstToken)).orElseThrow();
            issued.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
            emailTokenRepository.saveAndFlush(issued);

            // A resend supersedes the first link.
            Map<String, Object> resend = new LinkedHashMap<>();
            resend.put("email", email);
            mockMvc.perform(post("/api/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(resend)))
                    .andExpect(status().isAccepted());

            // Superseded is not the same fact as consumed, and the user should
            // not be told their old link "was already used".
            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verify(firstToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }

        @Test
        @DisplayName("login still works for an UNVERIFIED account — no gate in Phase 4")
        void loginIsNotGatedYet() throws Exception {
            String email = uniqueEmail("ungated");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            // The verification gate belongs to the final phase. If this ever
            // starts failing, a gate was enabled early.
            Map<String, Object> login = new LinkedHashMap<>();
            login.put("email", email);
            login.put("password", DEFAULT_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(login)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    @Test
    @DisplayName("the emailed link points at the frontend origin and carries the token")
    void linkTargetsFrontend() throws Exception {
        String email = uniqueEmail("linkshape");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());

        String text = mailbox.last().text();

        // The link must target the Next.js page, never the API - a GET on the
        // API would let a mail scanner consume the token.
        assertTrue(text.contains("/verify-email?token="), "unexpected link shape: " + text);
        assertNotNull(mailbox.lastToken());
    }
}
