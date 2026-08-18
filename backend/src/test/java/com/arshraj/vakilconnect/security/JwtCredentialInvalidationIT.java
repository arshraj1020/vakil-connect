package com.arshraj.vakilconnect.security;

import com.arshraj.vakilconnect.security.jwt.JwtService;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.support.EmailCaptureConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.SecretKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5: a credential change invalidates every outstanding JWT.
 *
 * THE HIGHEST-RISK BEHAVIOUR IN THE APPLICATION. This check runs on every
 * authenticated request, so a mistake either logs everyone out or fails to log
 * anyone out. These tests pin both directions.
 *
 * Tokens are minted here with the same signing key the application uses, read
 * from `jwt.secret`, so hand-crafted tokens are genuinely valid signatures and
 * the filter is exercised on its real path rather than on a rejection it would
 * have made anyway.
 */
@DisplayName("JWT credential-change invalidation")
@Import(EmailCaptureConfig.class)
class JwtCredentialInvalidationIT extends AbstractIntegrationTest {

    /** Any authenticated endpoint; the assertion is about the filter, not the body. */
    private static final String PROTECTED_ENDPOINT = "/api/users/me";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private String registerAndLogin(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());
        return login(email, DEFAULT_PASSWORD);
    }

    /**
     * Moves credentials_changed_at forward directly.
     *
     * Via JDBC because Phase 5 must not add a credential-change endpoint - that
     * is Phase 6's password reset. This simulates what Phase 6 (or the Phase 4
     * takeover) does to the column, without importing either.
     */
    private void bumpCredentials(String email, Instant to) {
        jdbcTemplate.update(
                "UPDATE users SET credentials_changed_at = ? WHERE email = ?",
                Timestamp.from(to), email);
    }

    private Instant storedCredentialsChangedAt(String email) {
        Timestamp ts = jdbcTemplate.queryForObject(
                "SELECT credentials_changed_at FROM users WHERE email = ?",
                Timestamp.class, email);
        return assertNotNullAndConvert(ts);
    }

    private static Instant assertNotNullAndConvert(Timestamp ts) {
        assertNotNull(ts, "credentials_changed_at is NOT NULL and must be present");
        return ts.toInstant();
    }

    private int callProtectedWith(String token) throws Exception {
        return mockMvc.perform(get(PROTECTED_ENDPOINT).header("Authorization", bearer(token)))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------- issuance

    @Nested
    @DisplayName("issuance")
    class Issuance {

        @Test
        @DisplayName("a newly issued JWT carries a cca claim matching the stored value")
        void newTokenCarriesCca() throws Exception {
            String email = uniqueEmail("ccaclaim");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            String token = login(email, DEFAULT_PASSWORD);

            Object raw = Jwts.parser().verifyWith(signingKey()).build()
                    .parseSignedClaims(token).getPayload()
                    .get(JwtService.CLAIM_CREDENTIALS_CHANGED_AT);

            assertNotNull(raw, "every new token must carry cca");

            long claimMillis = ((Number) raw).longValue();
            long storedMillis = storedCredentialsChangedAt(email).toEpochMilli();

            // Epoch MILLISECONDS, not seconds. A seconds value would be ~1000x
            // smaller and would silently compare wrong.
            assertEquals(storedMillis, claimMillis,
                    "cca must be the stored credentials_changed_at in epoch millis");
            assertTrue(claimMillis > 1_000_000_000_000L,
                    "cca looks like seconds, not milliseconds: " + claimMillis);
        }

        @Test
        @DisplayName("a newly issued JWT authenticates")
        void newTokenAuthenticates() throws Exception {
            assertEquals(200, callProtectedWith(registerAndLogin("ccahappy")));
        }
    }

    // ---------------------------------------------------------- invalidation

    @Nested
    @DisplayName("invalidation")
    class Invalidation {

        @Test
        @DisplayName("bumping credentials_changed_at rejects the previously issued JWT with 401")
        void staleTokenRejected() throws Exception {
            String email = uniqueEmail("ccastale");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            String token = login(email, DEFAULT_PASSWORD);
            assertEquals(200, callProtectedWith(token), "sanity: the token works first");

            // What a password reset or takeover does to the column.
            bumpCredentials(email, Instant.now().plus(1, ChronoUnit.MINUTES));

            // THE POINT OF THE WHOLE PHASE: the session is over immediately,
            // not when the 24h expiry eventually lapses.
            assertEquals(401, callProtectedWith(token));
        }

        @Test
        @DisplayName("a JWT issued AFTER the bump works")
        void freshTokenAfterBumpWorks() throws Exception {
            String email = uniqueEmail("ccafresh");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            bumpCredentials(email, Instant.now());

            // Invalidation must not be a one-way door: logging in again has to
            // produce a working session.
            assertEquals(200, callProtectedWith(login(email, DEFAULT_PASSWORD)));
        }

        @Test
        @DisplayName("an equal cca is accepted — a tie must not lock out the legitimate user")
        void equalCcaAccepted() throws Exception {
            String email = uniqueEmail("ccaequal");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            String token = login(email, DEFAULT_PASSWORD);

            // Rewrite the column to exactly the token's own value. The
            // comparison is `claim < stored` -> reject, so equality passes.
            // Were it `<=`, the user who just changed their password would be
            // locked out by their own fresh token.
            Object raw = Jwts.parser().verifyWith(signingKey()).build()
                    .parseSignedClaims(token).getPayload()
                    .get(JwtService.CLAIM_CREDENTIALS_CHANGED_AT);
            bumpCredentials(email, Instant.ofEpochMilli(((Number) raw).longValue()));

            assertEquals(200, callProtectedWith(token));
        }
    }

    // ------------------------------------------------------- malformed input

    @Nested
    @DisplayName("bad claims are 401, never 500")
    class BadClaims {

        /** Mints a validly SIGNED token with an arbitrary cca payload. */
        private String tokenWithCca(String email, Object ccaValue) {
            var builder = Jwts.builder()
                    .subject(email)
                    .issuedAt(new Date())
                    .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

            if (ccaValue != null) {
                builder.claim(JwtService.CLAIM_CREDENTIALS_CHANGED_AT, ccaValue);
            }
            return builder.signWith(signingKey()).compact();
        }

        @Test
        @DisplayName("a JWT with NO cca is rejected — every pre-Phase-5 token")
        void missingCcaRejected() throws Exception {
            String email = uniqueEmail("ccamissing");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            // Signature is valid, subject exists, not expired - the ONLY thing
            // wrong is the absent claim. This is exactly the shape of every
            // token issued before this phase, and the reason for the one-time
            // sign-out at deploy.
            assertEquals(401, callProtectedWith(tokenWithCca(email, null)));
        }

        @Test
        @DisplayName("a non-numeric cca is rejected with 401, NOT 500")
        void malformedCcaRejected() throws Exception {
            String email = uniqueEmail("ccagarbage");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            // A NumberFormatException escaping the filter would surface as 500.
            // An authentication path must never answer 500 to a bad credential.
            assertEquals(401, callProtectedWith(tokenWithCca(email, "not-a-number")));
        }

        @Test
        @DisplayName("a cca far in the past is rejected")
        void ancientCcaRejected() throws Exception {
            String email = uniqueEmail("ccaancient");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            assertEquals(401, callProtectedWith(tokenWithCca(email, 0L)));
        }

        @Test
        @DisplayName("an expired JWT is still rejected")
        void expiredTokenRejected() throws Exception {
            String email = uniqueEmail("ccaexpired");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(clientRegistration(email))))
                    .andExpect(status().isCreated());

            String expired = Jwts.builder()
                    .subject(email)
                    .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                    .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                    .claim(JwtService.CLAIM_CREDENTIALS_CHANGED_AT,
                            storedCredentialsChangedAt(email).toEpochMilli())
                    .signWith(signingKey())
                    .compact();

            // Expiry checking must survive the new claim logic.
            assertEquals(401, callProtectedWith(expired));
        }
    }

    // ---------------------------------------------------------- end-to-end

    @Test
    @DisplayName("a Phase 4 takeover invalidates the previous holder's JWT")
    void takeoverInvalidatesPreviousSession() throws Exception {
        String email = uniqueEmail("ccatakeover");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());

        String squatterToken = login(email, DEFAULT_PASSWORD);
        assertEquals(200, callProtectedWith(squatterToken));

        // Age the account past the 7-day takeover window. created_at is
        // updatable=false on BaseEntity, hence JDBC.
        jdbcTemplate.update(
                "UPDATE users SET created_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)), email);

        // The real owner claims the address. AuthServiceImpl bumps
        // credentials_changed_at as part of the takeover.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());

        // END TO END: the squatter's live session dies the moment the address
        // changes hands, rather than lingering for the rest of the 24h expiry.
        assertEquals(401, callProtectedWith(squatterToken));
    }

    @Test
    @DisplayName("a deactivated user is still rejected — isEnabled() semantics preserved")
    void deactivationStillEnforced() throws Exception {
        String email = uniqueEmail("ccadeactivated");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());

        String token = login(email, DEFAULT_PASSWORD);
        assertEquals(200, callProtectedWith(token));

        jdbcTemplate.update("UPDATE users SET active = false WHERE email = ?", email);

        /*
         * Guards the single most dangerous way AuthenticatedUser could be
         * wrong. isEnabled() must still mean `users.active`; if its polarity
         * were inverted, this returns 200 and every deactivated account keeps
         * full API access.
         */
        assertEquals(401, callProtectedWith(token));
    }

    @Test
    @DisplayName("a verified-but-unverified-email user still authenticates — no login gate")
    void emailVerificationDoesNotAffectAuthentication() throws Exception {
        // The account is unverified: registration never verifies it. If
        // AuthenticatedUser.isEnabled() had been wired to emailVerified instead
        // of active, this would 401 and a login gate would have shipped years
        // before the phase that introduces it.
        assertEquals(200, callProtectedWith(registerAndLogin("ccaunverified")));
    }
}
