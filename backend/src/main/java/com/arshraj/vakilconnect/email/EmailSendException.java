package com.arshraj.vakilconnect.email;

/**
 * A TRANSIENT transport failure. Another attempt could plausibly succeed.
 *
 * Thrown for 5xx, 429, and connection/read timeouts. This is the type
 * {@code @Retryable} matches on.
 *
 * Permanent failures use {@link PermanentEmailSendException}, a subclass that
 * the retry policy explicitly excludes. A SUBCLASS RATHER THAN A BOOLEAN FLAG
 * is deliberate: Spring Retry classifies on exception TYPE, so encoding the
 * decision in the type makes the policy compile-checked instead of dependent on
 * a SpEL expression that can only fail at runtime.
 *
 * NEVER carries the email body or a token - only the provider's status. A
 * provider error body can echo the request, and the request contains the link.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }

    /** True unless this is a permanent failure. */
    public boolean isRetryable() {
        return !(this instanceof PermanentEmailSendException);
    }
}
