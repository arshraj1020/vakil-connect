package com.arshraj.vakilconnect.email;

/**
 * Sends one email. The provider is an implementation detail.
 *
 * ONE METHOD, ON PURPOSE. Every implementation is then trivially substitutable,
 * and swapping Resend for SES later is a new class plus a property value rather
 * than a refactor.
 *
 * Implementations are selected by {@code vakilconnect.email.provider} - see
 * {@link ConsoleEmailSender} and {@link ResendEmailSender}. Exactly one is ever
 * present in a running context.
 */
public interface EmailService {

    /**
     * Delivers the message, or throws.
     *
     * @throws EmailSendException on a transport failure. Implementations must
     *         set {@code retryable} correctly: a 5xx or a timeout is worth
     *         another attempt, a 4xx validation error will fail identically
     *         forever and must not be retried.
     */
    void send(EmailMessage message);
}
