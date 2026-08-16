package com.arshraj.vakilconnect.identity.service;

import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates verification/reset tokens and hashes them for storage.
 *
 * WHY HMAC AND NOT A BARE SHA-256. A 256-bit random token is not brute-forceable
 * either way, so the margin is narrow - but it is real: a read-only SQL
 * injection or a leaked backup yields nothing usable without the pepper, which
 * lives in the environment rather than the database. The cost is one config
 * value.
 *
 * WHY NOT BCRYPT. Slow-by-design hashing exists to protect LOW-entropy secrets.
 * Against 256 bits of randomness it buys nothing, adds a CPU-exhaustion vector
 * on an unauthenticated endpoint, and silently truncates input at 72 bytes.
 *
 * WHY NOT CONSTANT-TIME COMPARISON. The usual advice targets fetch-then-equals.
 * Here the hash is the lookup key of a unique index, so there is no application
 * comparison to time, and no realistic timing signal from an index probe on a
 * 256-bit value.
 */
@Component
public class TokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    /** 32 bytes = 256 bits. Base64-URL encodes to 43 unpadded characters. */
    private static final int RAW_TOKEN_BYTES = 32;

    /** Length of a hex-encoded SHA-256 digest, and of the token_hash column. */
    public static final int HASH_LENGTH = 64;

    /**
     * SecureRandom IS thread-safe, so one shared instance is correct and avoids
     * re-seeding on every call. Contrast Mac below, which is not.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte[] pepper;

    public TokenHasher(IdentityProperties properties) {
        // @NotBlank on the property means this cannot be empty at runtime - the
        // application refuses to start instead, which is the same fail-fast
        // posture as JWT_SECRET.
        this.pepper = properties.tokenPepper().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A fresh 256-bit token. THE ONLY PLACE THE RAW VALUE EXISTS is the return
     * of this method and the email built from it - it is never persisted.
     *
     * Base64-URL without padding so the value is safe in a query string
     * unescaped. NOT UUID.randomUUID(), which carries only 122 bits and is not
     * guaranteed to come from a CSPRNG.
     */
    public String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * HMAC-SHA256 of the raw token under the configured pepper, as lowercase
     * hex.
     *
     * A NEW Mac PER CALL, deliberately. Mac is stateful and NOT thread-safe;
     * sharing one across request threads interleaves partial digests and
     * produces wrong hashes non-deterministically - the worst possible bug in
     * an authentication path. Instantiation is cheap relative to the database
     * round trip that follows.
     */
    public String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Raw token must not be null");
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            byte[] digest = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandated by the JDK, and the key is non-empty by
            // construction, so neither branch is reachable on a sane runtime.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
