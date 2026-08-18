package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.entity.EmailToken;
import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.identity.service.TokenHasher;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.support.EmailCaptureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/auth/resend-verification.
 *
 * The security property under test is that this endpoint cannot be used to
 * discover whether an address has an account.
 */
@DisplayName("Resend verification")
@Import(EmailCaptureConfig.class)
class ResendVerificationIT extends AbstractIntegrationTest {

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

    private MvcResult resend(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        return mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private String register(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());
        return email;
    }

    /**
     * Ages the newest token so the cooldown has elapsed.
     *
     * created_at is the cooldown's source of truth, so backdating it is exactly
     * equivalent to waiting - and does not add 60 seconds to the suite.
     */
    private void expireCooldown(String rawToken) {
        EmailToken token = emailTokenRepository
                .findByTokenHashWithUser(tokenHasher.hash(rawToken)).orElseThrow();
        token.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        emailTokenRepository.saveAndFlush(token);
    }

    // ------------------------------------------------------- happy path

    @Test
    @DisplayName("an eligible account gets a new token and the old one is superseded")
    void issuesNewTokenAndSupersedesOld() throws Exception {
        String email = register("resendok");
        String firstToken = mailbox.lastToken();
        expireCooldown(firstToken);
        mailbox.reset();

        resend(email);

        assertEquals(1, mailbox.count(), "a new verification email is queued");
        String secondToken = mailbox.lastToken();

        assertNotNull(emailTokenRepository
                .findByTokenHashWithUser(tokenHasher.hash(firstToken))
                .orElseThrow().getInvalidatedAt(),
                "the previous link must be superseded, or two links would work at once");
        assertNull(emailTokenRepository
                .findByTokenHashWithUser(tokenHasher.hash(secondToken))
                .orElseThrow().getInvalidatedAt());
    }

    @Test
    @DisplayName("resend requires no JWT")
    void requiresNoAuthentication() throws Exception {
        String email = register("resendnoauth");
        expireCooldown(mailbox.lastToken());

        // No Authorization header: a user who never received the first email
        // cannot log in to ask for another.
        assertEquals(202, resend(email).getResponse().getStatus());
    }

    // --------------------------------------------------------- cooldown

    @Test
    @DisplayName("a second resend inside the cooldown is 429 COOLDOWN_ACTIVE with Retry-After")
    void cooldownEnforced() throws Exception {
        String email = register("cooldown");
        // Registration itself just issued a token, so the cooldown is running.

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COOLDOWN_ACTIVE"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("the cooldown reads the database, so it survives a restart")
    void cooldownIsDurable() throws Exception {
        String email = register("durable");

        // Nothing in-memory is consulted: the check is MAX(created_at) on
        // email_tokens. An attacker who can force a redeploy - or who simply
        // waits for one - must not be able to reset it.
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isTooManyRequests());

        expireCooldown(mailbox.lastToken());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("a cooldown rejection issues no token and sends no email")
    void cooldownWritesNothing() throws Exception {
        String email = register("cooldownclean");
        mailbox.reset();

        resend(email);

        // The exception is thrown before any write, so the transaction rolls
        // back with nothing in it.
        assertEquals(0, mailbox.count());
    }

    // ----------------------------------------------------- enumeration

    @Test
    @DisplayName("an unknown address returns the SAME 202 and body as a real one")
    void unknownAddressIsIndistinguishable() throws Exception {
        String realEmail = register("enumreal");
        expireCooldown(mailbox.lastToken());
        mailbox.reset();

        MvcResult real = resend(realEmail);
        MvcResult ghost = resend(uniqueEmail("enumghost"));

        assertEquals(202, real.getResponse().getStatus());
        assertEquals(202, ghost.getResponse().getStatus());
        // Byte-identical bodies: the status code and the payload must both be
        // useless for deciding whether the account exists.
        assertEquals(real.getResponse().getContentAsString(),
                ghost.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("an already-verified address returns the same 202 and sends nothing")
    void verifiedAddressIsIndistinguishable() throws Exception {
        String email = register("enumverified");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", mailbox.lastToken()))))
                .andExpect(status().isOk());
        mailbox.reset();

        MvcResult result = resend(email);

        assertEquals(202, result.getResponse().getStatus());
        assertEquals(0, mailbox.count(),
                "a verified account must not be mailed another verification link");
    }

    @Test
    @DisplayName("a nonexistent address issues no token")
    void unknownAddressWritesNothing() throws Exception {
        resend(uniqueEmail("ghostwrite"));

        assertEquals(0, mailbox.count());
    }

    @Test
    @DisplayName("a malformed address is rejected by validation")
    void malformedEmailRejected() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());
    }
}
