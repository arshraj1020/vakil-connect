package com.arshraj.vakilconnect.email.event;

import com.arshraj.vakilconnect.email.EmailMessage;

/**
 * "Send this email once the current transaction commits."
 *
 * DELIBERATELY GENERIC. It carries a fully rendered {@link EmailMessage} and
 * nothing else - no user, no token, no notion of verification. That is what
 * lets the `email` package stay free of any dependency on `identity`: the
 * domain that wants an email renders its own subject and body, then publishes
 * this.
 *
 * PUBLISH IT INSIDE A TRANSACTION. The listener is bound to AFTER_COMMIT, and
 * a @TransactionalEventListener silently does nothing when no transaction is
 * active. EmailDispatchListener carries a guard that logs an ERROR in exactly
 * that case, so the failure is loud rather than an email that never arrives.
 *
 * @param message the rendered email; must not be null
 */
public record SendEmailRequestedEvent(EmailMessage message) {

    public SendEmailRequestedEvent {
        if (message == null) {
            throw new IllegalArgumentException("EmailMessage must not be null");
        }
    }

    /**
     * REDACTED. A record's generated toString() would print the wrapped
     * message, and although EmailMessage redacts itself, relying on that
     * indirection is fragile - a future change there would silently start
     * leaking bodies through this class. Redacting at both levels means neither
     * one alone is load-bearing.
     */
    @Override
    public String toString() {
        return "SendEmailRequestedEvent{tag=" + message.tag() + ", body=<redacted>}";
    }
}
