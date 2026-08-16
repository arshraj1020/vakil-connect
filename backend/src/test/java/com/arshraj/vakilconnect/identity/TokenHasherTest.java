package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import com.arshraj.vakilconnect.identity.service.TokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test - no Spring context, so it costs milliseconds rather than a
 * container start-up. TokenHasher's only collaborator is a value object.
 */
@DisplayName("TokenHasher")
class TokenHasherTest {

    private static final String PEPPER_A = "pepper-a";
    private static final String PEPPER_B = "pepper-b";

    private static TokenHasher hasherWith(String pepper) {
        return new TokenHasher(new IdentityProperties(
                pepper,
                "http://localhost:3000",
                false,
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofSeconds(60),
                Duration.ofDays(7),
                true,
                Duration.ofDays(30),
                Duration.ofDays(30)));
    }

    private final TokenHasher hasher = hasherWith(PEPPER_A);

    @Test
    @DisplayName("hashing is deterministic for the same input and pepper")
    void deterministic() {
        String raw = hasher.generateRawToken();

        assertEquals(hasher.hash(raw), hasher.hash(raw));
        // A second instance with the same pepper must agree, or a rolling
        // restart mid-flow would invalidate every outstanding link.
        assertEquals(hasher.hash(raw), hasherWith(PEPPER_A).hash(raw));
    }

    @Test
    @DisplayName("different input produces a different hash")
    void differentInput() {
        assertNotEquals(hasher.hash("token-one"), hasher.hash("token-two"));
    }

    @Test
    @DisplayName("the same input under a different pepper produces a different hash")
    void differentPepper() {
        String raw = hasher.generateRawToken();

        // This is the whole point of the pepper: a stolen database dump is
        // useless without the environment value.
        assertNotEquals(hasherWith(PEPPER_A).hash(raw), hasherWith(PEPPER_B).hash(raw));
    }

    @Test
    @DisplayName("output is exactly 64 lowercase hex characters")
    void outputShape() {
        String hash = hasher.hash(hasher.generateRawToken());

        // 64 is not cosmetic - it is the width of the token_hash column, and a
        // longer value would be a runtime insert failure in production.
        assertEquals(TokenHasher.HASH_LENGTH, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"), "expected lowercase hex, got: " + hash);
    }

    @Test
    @DisplayName("the hash is never equal to the raw token")
    void hashIsNotTheRawToken() {
        String raw = hasher.generateRawToken();

        // Guards the one mistake that would silently destroy the whole scheme:
        // a hasher that returns its input.
        assertNotEquals(raw, hasher.hash(raw));
    }

    @Test
    @DisplayName("raw tokens are 43 chars of URL-safe Base64 and do not repeat")
    void rawTokenShapeAndUniqueness() {
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            String raw = hasher.generateRawToken();

            // 32 bytes -> 43 unpadded Base64 chars. No '+', '/' or '=' so the
            // value survives a query string without escaping.
            assertEquals(43, raw.length(), "unexpected token length: " + raw);
            assertTrue(raw.matches("^[A-Za-z0-9_-]{43}$"), "not URL-safe: " + raw);
            assertTrue(seen.add(raw), "SecureRandom repeated a token: " + raw);
        }
    }

    @Test
    @DisplayName("rejects a null token rather than hashing the string \"null\"")
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }
}
