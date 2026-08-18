package com.arshraj.vakilconnect.email;

/**
 * One outbound email, fully rendered.
 *
 * DELIBERATELY GENERIC. This carries no notion of verification, tokens, users
 * or any other domain concept - the caller renders its own subject and body and
 * hands over a finished message. That is what keeps the `email` package free of
 * any dependency on `identity`, so the transport can be reused for appointment
 * reminders or booking confirmations without change.
 *
 * @param to       recipient address
 * @param subject  subject line
 * @param html     HTML body; may be null if {@code text} is present
 * @param text     plain-text body; may be null if {@code html} is present
 * @param tag      short, NON-SENSITIVE label used as the `type` metric tag and
 *                 in log lines - e.g. "verification". Never an address, never a
 *                 token, never anything user-supplied
 */
public record EmailMessage(
        String to,
        String subject,
        String html,
        String text,
        String tag
) {

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email recipient must not be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject must not be blank");
        }
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new IllegalArgumentException("Email must have an html or text body");
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Email tag must not be blank");
        }
    }

    /**
     * REDACTED, AND THIS IS LOAD-BEARING.
     *
     * A record's generated toString() prints every component, so the default
     * would put the full body - which for a verification email is a working
     * single-use link - into any log line that happens to include this object.
     * Only the tag and subject are safe to print; the recipient is personal
     * data and the bodies are the secret.
     */
    @Override
    public String toString() {
        return "EmailMessage{tag=" + tag + ", subject=" + subject + ", to=<redacted>, body=<redacted>}";
    }
}
