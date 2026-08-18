package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.entity.EmailToken;
import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.identity.service.PasswordResetEmailFactory;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6: password reset end to end.
 *
 * The property that matters most here is the one at the intersection with
 * Phase 5: completing a reset must kill every session the previous password
 * held. A reset that leaves the attacker logged in is security theatre.
 */
@DisplayName("Password reset")
@Import(EmailCaptureConfig.class)
class PasswordResetIT extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "brand-new-password-123";
    private static final String PROTECTED_ENDPOINT = "/api/users/me";

    @Autowired
    private EmailCaptureConfig.RecordingEmailSender mailbox;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearMailbox() {
        mailbox.reset();
    }

    // ------------------------------------------------------------- helpers

    private String register(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());
        return email;
    }

    private MvcResult forgot(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        return mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult reset(String token, String newPassword) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("newPassword", newPassword);
        return mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /**
     * Registration issues a VERIFY_EMAIL token, which starts the shared
     * cooldown clock. Backdate it so the reset request is not refused with 429
     * for reasons unrelated to what each test is checking.
     */
    private void clearCooldown(String rawToken) {
        EmailToken token = emailTokenRepository
                .findByTokenHashWithUser(tokenHasher.hash(rawToken)).orElseThrow();
        token.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        emailTokenRepository.saveAndFlush(token);
    }

    /** Registers, clears the cooldown, requests a reset, returns the raw token. */
    private String registerAndRequestReset(String prefix) throws Exception {
        String email = register(prefix);
        clearCooldown(mailbox.lastToken());
        mailbox.reset();

        assertEquals(202, forgot(email).getResponse().getStatus());
        return mailbox.lastToken();
    }

    private int callProtectedWith(String token) throws Exception {
        return mockMvc.perform(get(PROTECTED_ENDPOINT).header("Authorization", bearer(token)))
                .andReturn().getResponse().getStatus();
    }

    private int attemptLogin(String email, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------ forgot-password

    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class Forgot {

        @Test
        @DisplayName("an existing account is emailed a reset link")
        void existingAccountGetsLink() throws Exception {
            String email = register("resetok");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();

            assertEquals(202, forgot(email).getResponse().getStatus());

            assertEquals(1, mailbox.count());
            assertEquals(PasswordResetEmailFactory.RESET_TAG, mailbox.last().tag());
        }

        @Test
        @DisplayName("an unknown address returns the SAME 202 and byte-identical body")
        void unknownAddressIsIndistinguishable() throws Exception {
            String realEmail = register("enumreal");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();

            MvcResult real = forgot(realEmail);
            MvcResult ghost = forgot(uniqueEmail("enumghost"));

            assertEquals(202, real.getResponse().getStatus());
            assertEquals(202, ghost.getResponse().getStatus());
            // Status AND payload must both be useless for deciding whether the
            // account exists.
            assertEquals(real.getResponse().getContentAsString(),
                    ghost.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("an unknown address issues no token and sends nothing")
        void unknownAddressWritesNothing() throws Exception {
            forgot(uniqueEmail("ghostwrite"));
            assertEquals(0, mailbox.count());
        }

        @Test
        @DisplayName("an UNVERIFIED account may still reset — otherwise it is stranded")
        void unverifiedAccountMayReset() throws Exception {
            String email = register("unverifiedreset");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();

            // Registration never verifies. Refusing here would trap a user who
            // never clicked verify and then forgot their password.
            assertEquals(202, forgot(email).getResponse().getStatus());
            assertEquals(1, mailbox.count());
        }

        @Test
        @DisplayName("a DEACTIVATED account gets the same 202 but no email")
        void deactivatedAccountSendsNothing() throws Exception {
            String email = register("deactivatedreset");
            clearCooldown(mailbox.lastToken());

            User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
            user.setActive(false);
            userRepositoryForSupport.saveAndFlush(user);
            mailbox.reset();

            // Same public answer - no enumeration - but letting a disabled
            // account rotate credentials would partially undo the moderation.
            assertEquals(202, forgot(email).getResponse().getStatus());
            assertEquals(0, mailbox.count());
        }

        @Test
        @DisplayName("the response never carries a token")
        void responseHasNoToken() throws Exception {
            String email = register("notokenleak");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();

            String body = forgot(email).getResponse().getContentAsString();
            String issuedToken = mailbox.lastToken();

            assertFalse(body.contains(issuedToken),
                    "the reset token must never appear in an API response");
            assertFalse(body.toLowerCase().contains("token"), body);
        }

        @Test
        @DisplayName("a second request inside the cooldown is 429")
        void cooldownEnforced() throws Exception {
            String email = register("resetcooldown");
            clearCooldown(mailbox.lastToken());

            assertEquals(202, forgot(email).getResponse().getStatus());
            // The reset just issued starts its own cooldown.
            assertEquals(429, forgot(email).getResponse().getStatus());
        }

        @Test
        @DisplayName("a malformed address is rejected by validation")
        void malformedEmailRejected() throws Exception {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "not-an-email"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------- tokens

    @Nested
    @DisplayName("reset token")
    class Tokens {

        @Test
        @DisplayName("only the HASH is stored — the emailed token is not in the database")
        void rawTokenNeverPersisted() throws Exception {
            String rawToken = registerAndRequestReset("rawreset");

            assertTrue(emailTokenRepository.findByTokenHashWithUser(rawToken).isEmpty(),
                    "the raw token must not be a stored hash");
            assertTrue(emailTokenRepository
                            .findByTokenHashWithUser(tokenHasher.hash(rawToken)).isPresent(),
                    "the hash of the emailed token must be stored");
        }

        @Test
        @DisplayName("requesting again supersedes the previous link")
        void reissueSupersedesPrevious() throws Exception {
            String email = register("resetreissue");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();

            forgot(email);
            String first = mailbox.lastToken();
            clearCooldown(first);

            forgot(email);
            String second = mailbox.lastToken();

            assertNotNull(emailTokenRepository
                            .findByTokenHashWithUser(tokenHasher.hash(first))
                            .orElseThrow().getInvalidatedAt(),
                    "two working reset links would double the exposure window");
            assertNotEquals(first, second);

            // The superseded link is refused, and NOT as "already used".
            assertEquals(400, reset(first, NEW_PASSWORD).getResponse().getStatus());
        }

        @Test
        @DisplayName("an expired token is 410 and does not change the password")
        void expiredTokenRejected() throws Exception {
            String email = register("resetexpired");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();
            forgot(email);
            String rawToken = mailbox.lastToken();

            EmailToken stored = emailTokenRepository
                    .findByTokenHashWithUser(tokenHasher.hash(rawToken)).orElseThrow();
            stored.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            emailTokenRepository.saveAndFlush(stored);

            assertEquals(410, reset(rawToken, NEW_PASSWORD).getResponse().getStatus());
            assertEquals(200, attemptLogin(email, DEFAULT_PASSWORD),
                    "the original password must still work");
        }

        @Test
        @DisplayName("a token can be used only once")
        void singleUse() throws Exception {
            String rawToken = registerAndRequestReset("resetonce");

            assertEquals(200, reset(rawToken, NEW_PASSWORD).getResponse().getStatus());
            assertEquals(409, reset(rawToken, "another-password-456").getResponse().getStatus());
        }

        @Test
        @DisplayName("an unknown token is 400 and changes nothing")
        void unknownTokenRejected() throws Exception {
            String email = register("resetunknown");

            assertEquals(400,
                    reset("ThisTokenWasNeverIssuedAnywhere12345", NEW_PASSWORD)
                            .getResponse().getStatus());
            assertEquals(200, attemptLogin(email, DEFAULT_PASSWORD));
        }

        @Test
        @DisplayName("a VERIFY_EMAIL token cannot be used to reset a password")
        void wrongTokenTypeRejected() throws Exception {
            String email = register("resetwrongtype");
            String verifyToken = mailbox.lastToken();

            // Type is part of the atomic consume predicate. Without it, a
            // verification link would be a password-reset authorisation.
            assertEquals(400, reset(verifyToken, NEW_PASSWORD).getResponse().getStatus());
            assertEquals(200, attemptLogin(email, DEFAULT_PASSWORD));
        }

        @Test
        @DisplayName("a too-short password is rejected by the shared rule")
        void weakPasswordRejected() throws Exception {
            String rawToken = registerAndRequestReset("resetweak");

            // Same constraint object registration uses, so the two cannot drift.
            assertEquals(400, reset(rawToken, "short").getResponse().getStatus());
        }
    }

    // ------------------------------------------------------- the reset itself

    @Nested
    @DisplayName("POST /api/auth/reset-password")
    class Reset {

        @Test
        @DisplayName("the old password stops working and the new one starts")
        void passwordIsReplaced() throws Exception {
            String email = register("resetswap");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();
            forgot(email);

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "token", mailbox.lastToken(),
                                    "newPassword", NEW_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reset").value(true))
                    // D9: no session is handed out by a flow whose premise is
                    // "we are not sure who you are".
                    .andExpect(jsonPath("$.token").doesNotHaveJsonPath());

            assertEquals(401, attemptLogin(email, DEFAULT_PASSWORD));
            assertEquals(200, attemptLogin(email, NEW_PASSWORD));
        }

        @Test
        @DisplayName("the new password is BCrypt-encoded, never stored in plaintext")
        void passwordIsEncoded() throws Exception {
            String email = register("resetencoded");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();
            forgot(email);

            reset(mailbox.lastToken(), NEW_PASSWORD);

            String storedHash = userRepositoryForSupport.findByEmail(email)
                    .orElseThrow().getPasswordHash();

            assertNotEquals(NEW_PASSWORD, storedHash);
            assertTrue(storedHash.startsWith("$2"), "expected a BCrypt hash, got: " + storedHash);
            assertTrue(passwordEncoder.matches(NEW_PASSWORD, storedHash));
        }

        @Test
        @DisplayName("resetting marks the account email-verified")
        void resetVerifiesEmail() throws Exception {
            String email = register("resetverifies");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();
            forgot(email);

            assertFalse(userRepositoryForSupport.findByEmail(email).orElseThrow().isEmailVerified());

            reset(mailbox.lastToken(), NEW_PASSWORD);

            // Reaching the link proved mailbox control; leaving it unverified
            // would strand the user in a loop.
            assertTrue(userRepositoryForSupport.findByEmail(email).orElseThrow().isEmailVerified());
        }

        @Test
        @DisplayName("a still-live verification token is invalidated by the reset")
        void resetInvalidatesVerificationToken() throws Exception {
            String email = register("resetkillsverify");
            String verifyToken = mailbox.lastToken();
            clearCooldown(verifyToken);
            mailbox.reset();
            forgot(email);

            reset(mailbox.lastToken(), NEW_PASSWORD);

            assertNotNull(emailTokenRepository
                            .findByTokenHashWithUser(tokenHasher.hash(verifyToken))
                            .orElseThrow().getInvalidatedAt(),
                    "no link may outlive the reset that superseded it");
        }
    }

    // ----------------------------------------------- Phase 5 intersection

    @Test
    @DisplayName("a JWT held before the reset is rejected afterwards — THE point of the feature")
    void resetInvalidatesExistingSessions() throws Exception {
        String email = register("resetkillsjwt");
        clearCooldown(mailbox.lastToken());

        String oldJwt = login(email, DEFAULT_PASSWORD);
        assertEquals(200, callProtectedWith(oldJwt), "sanity: the session works first");

        mailbox.reset();
        forgot(email);
        assertEquals(200, reset(mailbox.lastToken(), NEW_PASSWORD).getResponse().getStatus());

        /*
         * Without the credentialsChangedAt bump feeding Phase 5's cca check,
         * an attacker holding a stolen token would keep full access for up to
         * 24 hours after the victim "secured" their account.
         */
        assertEquals(401, callProtectedWith(oldJwt));
    }

    @Test
    @DisplayName("a JWT issued after the reset works")
    void newSessionAfterResetWorks() throws Exception {
        String email = register("resetnewjwt");
        clearCooldown(mailbox.lastToken());
        mailbox.reset();
        forgot(email);
        reset(mailbox.lastToken(), NEW_PASSWORD);

        // Invalidation must not be a one-way door.
        assertEquals(200, callProtectedWith(login(email, NEW_PASSWORD)));
    }

    @Test
    @DisplayName("credentialsChangedAt moves forward")
    void credentialsChangedAtBumped() throws Exception {
        String email = register("resetbump");
        clearCooldown(mailbox.lastToken());
        mailbox.reset();

        Instant before = userRepositoryForSupport.findByEmail(email)
                .orElseThrow().getCredentialsChangedAt();

        forgot(email);
        reset(mailbox.lastToken(), NEW_PASSWORD);

        Instant after = userRepositoryForSupport.findByEmail(email)
                .orElseThrow().getCredentialsChangedAt();

        assertTrue(after.isAfter(before),
                "credentialsChangedAt must advance, or Phase 5 cannot invalidate anything");
    }

    // ---------------------------------------------------------------- email

    @Nested
    @DisplayName("email")
    class Emails {

        @Test
        @DisplayName("the link targets APP_PUBLIC_BASE_URL and carries the token")
        void linkShape() throws Exception {
            String rawToken = registerAndRequestReset("resetlink");

            String text = mailbox.last().text();

            // Frontend page, never the API - a GET on the API would let a mail
            // scanner consume the token before the user clicked.
            assertTrue(text.contains("/reset-password?token="), "unexpected link: " + text);
            assertTrue(text.contains(rawToken), "the link must carry the token");
        }

        @Test
        @DisplayName("a successful reset sends a password-changed notification")
        void notificationSent() throws Exception {
            String email = register("resetnotify");
            clearCooldown(mailbox.lastToken());
            mailbox.reset();

            forgot(email);
            // Capture BEFORE clearing, so the assertion below sees only the
            // notification and cannot accidentally pass on the reset email.
            String rawToken = mailbox.lastToken();
            mailbox.reset();

            assertEquals(200, reset(rawToken, NEW_PASSWORD).getResponse().getStatus());

            /*
             * This notification is a security control, not a courtesy: it is
             * the only signal the real owner gets if somebody else completed a
             * reset against their mailbox.
             */
            assertEquals(1, mailbox.count());
            assertEquals(PasswordResetEmailFactory.CHANGED_TAG, mailbox.last().tag());
            assertFalse(mailbox.last().text().contains("token="),
                    "the notification must carry no link and no token");
        }

        @Test
        @DisplayName("a failed reset sends nothing")
        void failedResetSendsNothing() throws Exception {
            register("resetnomail");
            mailbox.reset();

            reset("ThisTokenWasNeverIssuedAnywhere12345", NEW_PASSWORD);

            // The transaction rolls back before the AFTER_COMMIT listener could
            // ever fire.
            assertEquals(0, mailbox.count());
        }
    }
}
