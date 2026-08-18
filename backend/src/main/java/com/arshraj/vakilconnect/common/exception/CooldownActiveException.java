package com.arshraj.vakilconnect.common.exception;

import java.time.Duration;

/**
 * A verification email was requested again too soon. Maps to HTTP 429 with code
 * COOLDOWN_ACTIVE.
 *
 * DISTINCT FROM THE CONCURRENT-RACE OUTCOME. This is the ORDINARY path: the
 * cooldown was detected by reading the newest token's created_at, before
 * anything was written. A genuine race - two requests that both pass this check
 * and then collide on `uq_email_tokens_live` - surfaces instead as
 * DataIntegrityViolationException and is answered 409 RESOURCE_CONFLICT. Two
 * different situations, two different codes, and neither is caught inside an
 * aborted transaction.
 *
 * Carries the remaining wait so the controller can emit `Retry-After`.
 */
public class CooldownActiveException extends RuntimeException {

    public static final String CODE = "COOLDOWN_ACTIVE";

    private final Duration retryAfter;

    public CooldownActiveException(Duration retryAfter) {
        super("Please wait before requesting another email.");
        this.retryAfter = retryAfter;
    }

    /** Never negative; rounded up so a sub-second remainder still says 1. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.plusMillis(999).toSeconds();
        return Math.max(seconds, 1);
    }
}
