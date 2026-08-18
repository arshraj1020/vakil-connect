package com.arshraj.vakilconnect.email;

/**
 * A PERMANENT transport failure. Retrying is pointless.
 *
 * Thrown for 4xx other than 429: a malformed address, an unverified sending
 * domain, a bad API key. Every one of these will fail identically on the second
 * and third attempt, so retrying only burns provider quota and delays the
 * failure signal by the length of the backoff.
 *
 * Excluded from retry via {@code noRetryFor} on
 * {@link ResendEmailSender#send}. Because {@code noRetryFor} takes precedence
 * over {@code retryFor}, this subclass is skipped even though its supertype is
 * the retryable one.
 */
public class PermanentEmailSendException extends EmailSendException {

    public PermanentEmailSendException(String message) {
        super(message);
    }
}
