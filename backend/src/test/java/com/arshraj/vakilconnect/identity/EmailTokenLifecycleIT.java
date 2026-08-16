package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.common.exception.TokenAlreadyUsedException;
import com.arshraj.vakilconnect.common.exception.TokenExpiredException;
import com.arshraj.vakilconnect.common.exception.TokenInvalidException;
import com.arshraj.vakilconnect.identity.entity.EmailToken;
import com.arshraj.vakilconnect.identity.entity.EmailTokenType;
import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.identity.service.TokenHasher;
import com.arshraj.vakilconnect.identity.service.VerificationTokenService;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token lifecycle against the real V7 schema.
 *
 * The application context starting at all is itself an assertion here: the new
 * EmailToken entity is mapped against the already-deployed `email_tokens` table
 * with `ddl-auto: validate`, so a wrong column name, a varchar(255) enum, or an
 * accidental BaseEntity inheritance would fail every test in the suite rather
 * than just this class.
 *
 * No @Transactional on the class: several assertions depend on a bulk UPDATE
 * having actually reached the database, and a surrounding rolled-back
 * transaction would hide exactly the behaviour under test.
 */
@DisplayName("Email token lifecycle")
class EmailTokenLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    /** A committed CLIENT to own the tokens. Unique per call. */
    private User newUser() {
        User user = new User();
        user.setFullName("Token Fixture");
        user.setEmail(distinctEmail("tokenfixture"));
        user.setPasswordHash(passwordEncoderForSupport.encode(DEFAULT_PASSWORD));
        user.setPhoneNumber("9876543210");
        user.setRole(Role.CLIENT);
        user.setActive(true);
        return userRepositoryForSupport.save(user);
    }

    private Optional<EmailToken> reload(String rawToken) {
        return emailTokenRepository.findByTokenHashWithUser(tokenHasher.hash(rawToken));
    }

    // --------------------------------------------------------------- issuing

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("persists the hash and never the raw token")
        void persistsOnlyTheHash() {
            User user = newUser();

            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            EmailToken stored = reload(raw).orElseThrow();

            // The single most important assertion in this class. If the raw
            // token were ever written, a database dump would be a set of
            // working links.
            assertNotEquals(raw, stored.getTokenHash());
            assertEquals(tokenHasher.hash(raw), stored.getTokenHash());
            assertEquals(TokenHasher.HASH_LENGTH, stored.getTokenHash().length());
        }

        @Test
        @DisplayName("sets type, expiry and createdAt")
        void setsFields() {
            User user = newUser();
            Instant before = Instant.now();

            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            EmailToken stored = reload(raw).orElseThrow();

            assertEquals(EmailTokenType.VERIFY_EMAIL, stored.getType());
            assertEquals(user.getId(), stored.getUser().getId());
            assertNotNull(stored.getCreatedAt());
            assertNull(stored.getUsedAt());
            assertNull(stored.getInvalidatedAt());

            // 24h TTL from application.yaml. Asserting a window rather than an
            // exact instant, since the service reads its own clock.
            assertTrue(stored.getExpiresAt().isAfter(before.plus(Duration.ofHours(23))));
            assertTrue(stored.getExpiresAt().isBefore(before.plus(Duration.ofHours(25))));
        }

        @Test
        @DisplayName("uses the reset TTL for RESET_PASSWORD, not the verification TTL")
        void perTypeTtl() {
            User user = newUser();
            Instant before = Instant.now();

            String raw = tokenService.issue(user, EmailTokenType.RESET_PASSWORD);
            EmailToken stored = reload(raw).orElseThrow();

            // 30 minutes, deliberately far shorter than verification.
            assertTrue(stored.getExpiresAt().isBefore(before.plus(Duration.ofHours(1))));
        }

        @Test
        @DisplayName("a replacement invalidates the previous live token")
        void replacementInvalidatesPrevious() {
            User user = newUser();

            String first = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            String second = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            // Without this, uq_email_tokens_live would have rejected the second
            // insert - so a passing test here also proves the ordering inside
            // issue() is correct.
            assertNotNull(reload(first).orElseThrow().getInvalidatedAt());
            assertNull(reload(second).orElseThrow().getInvalidatedAt());
        }

        @Test
        @DisplayName("one live token of each type may coexist for a user")
        void oneLivePerType() {
            User user = newUser();

            String verify = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            String reset = tokenService.issue(user, EmailTokenType.RESET_PASSWORD);

            // A user mid-verification must still be able to reset a password.
            assertNull(reload(verify).orElseThrow().getInvalidatedAt());
            assertNull(reload(reset).orElseThrow().getInvalidatedAt());
        }
    }

    // ------------------------------------------------------------- consuming

    @Nested
    @DisplayName("consume")
    class Consume {

        @Test
        @DisplayName("returns the owner and marks the token used")
        void happyPath() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            User returned = tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL);

            assertEquals(user.getId(), returned.getId());
            // The JOIN FETCH is what makes this safe outside the transaction.
            assertEquals(user.getEmail(), returned.getEmail());
            assertNotNull(reload(raw).orElseThrow().getUsedAt());
        }

        @Test
        @DisplayName("a second consume of the same token fails")
        void secondConsumeFails() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL);

            assertThrows(TokenAlreadyUsedException.class,
                    () -> tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL));
        }

        @Test
        @DisplayName("an expired token fails with TOKEN_EXPIRED")
        void expiredFails() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            // Backdate rather than sleep. Expiry is evaluated in the database,
            // so moving the row's expiry is the honest way to test it.
            EmailToken stored = reload(raw).orElseThrow();
            stored.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            emailTokenRepository.saveAndFlush(stored);

            assertThrows(TokenExpiredException.class,
                    () -> tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL));
        }

        @Test
        @DisplayName("an invalidated token fails with TOKEN_INVALID, not ALREADY_USED")
        void invalidatedFails() {
            User user = newUser();
            String first = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            // Superseded is not the same fact as consumed, and the user should
            // not be told their old link "was already used".
            assertThrows(TokenInvalidException.class,
                    () -> tokenService.consume(first, EmailTokenType.VERIFY_EMAIL));
        }

        @Test
        @DisplayName("an unknown token fails with TOKEN_INVALID")
        void unknownFails() {
            assertThrows(TokenInvalidException.class,
                    () -> tokenService.consume(tokenHasher.generateRawToken(),
                            EmailTokenType.VERIFY_EMAIL));
        }

        @Test
        @DisplayName("presenting a token to the wrong flow fails")
        void wrongTypeFails() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            // A verification link must not double as a password-reset
            // authorisation.
            assertThrows(TokenInvalidException.class,
                    () -> tokenService.consume(raw, EmailTokenType.RESET_PASSWORD));

            assertNull(reload(raw).orElseThrow().getUsedAt(),
                    "a failed consume must not mark the token used");
        }

        @Test
        @DisplayName("null and blank are rejected without a database round trip")
        void rejectsEmpty() {
            assertThrows(TokenInvalidException.class,
                    () -> tokenService.consume(null, EmailTokenType.VERIFY_EMAIL));
            assertThrows(TokenInvalidException.class,
                    () -> tokenService.consume("   ", EmailTokenType.VERIFY_EMAIL));
        }
    }

    // ---------------------------------------------------------------- purge

    @Nested
    @DisplayName("purge")
    class Purge {

        @Test
        @DisplayName("removes tokens terminal for longer than the retention window")
        void removesOldTerminal() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL);

            // Retention is 30 days; backdate well past it.
            EmailToken stored = reload(raw).orElseThrow();
            stored.setUsedAt(Instant.now().minus(Duration.ofDays(60)));
            emailTokenRepository.saveAndFlush(stored);

            tokenService.purgeExpired();

            assertTrue(reload(raw).isEmpty());
        }

        @Test
        @DisplayName("leaves live tokens alone regardless of age")
        void keepsLive() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

            // Old AND expired, but neither used nor invalidated - still evidence.
            EmailToken stored = reload(raw).orElseThrow();
            stored.setCreatedAt(Instant.now().minus(Duration.ofDays(365)));
            stored.setExpiresAt(Instant.now().minus(Duration.ofDays(364)));
            emailTokenRepository.saveAndFlush(stored);

            tokenService.purgeExpired();

            assertTrue(reload(raw).isPresent(),
                    "purge must only remove TERMINAL tokens");
        }

        @Test
        @DisplayName("leaves recently consumed tokens alone")
        void keepsRecentTerminal() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL);

            tokenService.purgeExpired();

            // The audit trail has to survive the retention window.
            assertTrue(reload(raw).isPresent());
        }

        @Test
        @DisplayName("does not delete users")
        void doesNotTouchUsers() {
            User user = newUser();
            String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
            tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL);

            EmailToken stored = reload(raw).orElseThrow();
            stored.setUsedAt(Instant.now().minus(Duration.ofDays(60)));
            emailTokenRepository.saveAndFlush(stored);

            tokenService.purgeExpired();

            // Phase scope guard: unverified-account purging is a later phase and
            // must not creep in here via a cascade or a stray delete.
            assertTrue(userRepositoryForSupport.findById(user.getId()).isPresent());
        }
    }

    // --------------------------------------------------------- invalidateAll

    @Test
    @DisplayName("invalidateAll supersedes every live token of one type")
    void invalidateAll() {
        User user = newUser();
        String verify = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);
        String reset = tokenService.issue(user, EmailTokenType.RESET_PASSWORD);

        int affected = tokenService.invalidateAll(user.getId(), EmailTokenType.RESET_PASSWORD);

        assertEquals(1, affected);
        assertNotNull(reload(reset).orElseThrow().getInvalidatedAt());
        // Scoped by type - killing reset links must not log the user out of
        // their pending verification.
        assertNull(reload(verify).orElseThrow().getInvalidatedAt());
    }
}
