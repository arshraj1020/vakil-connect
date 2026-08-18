package com.arshraj.vakilconnect.identity.service;

import com.arshraj.vakilconnect.email.EmailMessage;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Renders the two password-reset emails: the reset link, and the
 * after-the-fact "your password changed" notice.
 *
 * SAME BOUNDARY AS VerificationEmailFactory. This lives in `identity` and knows
 * about reset flows; the `email` package knows about transport and nothing
 * else. Neither imports a provider. There is exactly ONE email transport in
 * this application and this class does not add another - it produces a finished
 * EmailMessage and hands it to the existing pipeline.
 *
 * Links are built from IdentityProperties.publicBaseUrl - the value Phase 1
 * introduced. No second base-URL property exists anywhere, deliberately.
 */
@Component
public class PasswordResetEmailFactory {

    /** Metric tag and log label for the reset link. Fixed, non-sensitive. */
    public static final String RESET_TAG = "password-reset";

    /** Metric tag for the post-change notification. */
    public static final String CHANGED_TAG = "password-changed";

    /** The frontend route that handles the token. */
    private static final String RESET_PATH = "/reset-password";

    private final IdentityProperties properties;

    public PasswordResetEmailFactory(IdentityProperties properties) {
        this.properties = properties;
    }

    /**
     * The email carrying the reset link.
     *
     * @param rawToken the single-use secret. Placed in the link and NOWHERE
     *                 else - not logged, not stored, redacted by
     *                 EmailMessage.toString().
     */
    public EmailMessage createResetEmail(String recipientEmail, String fullName, String rawToken) {
        String link = resetLink(rawToken);
        long minutes = properties.resetTokenTtl().toMinutes();
        String name = greetingName(fullName);

        String text = """
                Hi %s,

                We received a request to reset your VakilConnect password.

                %s

                This link expires in %d minutes and can only be used once.

                If you did not request this, you can ignore this email - your password
                has not been changed, and the link above will expire on its own.

                - VakilConnect
                """.formatted(name, link, minutes);

        String html = """
                <p>Hi %s,</p>
                <p>We received a request to reset your VakilConnect password.</p>
                <p><a href="%s">Reset my password</a></p>
                <p>Or paste this link into your browser:<br><span>%s</span></p>
                <p>This link expires in %d minutes and can only be used once.</p>
                <p>If you did not request this, you can ignore this email — your password
                has not been changed, and the link above will expire on its own.</p>
                <p>— VakilConnect</p>
                """.formatted(escape(name), link, link, minutes);

        return new EmailMessage(recipientEmail, "Reset your VakilConnect password",
                html, text, RESET_TAG);
    }

    /**
     * Sent AFTER the password actually changes.
     *
     * THIS IS A SECURITY CONTROL, not a courtesy. It is the only signal the
     * real owner gets if somebody else completed a reset against their mailbox,
     * and it is the reason an account takeover cannot be silent. It carries no
     * link and no token - there is nothing here worth stealing.
     */
    public EmailMessage createPasswordChangedEmail(String recipientEmail, String fullName) {
        String name = greetingName(fullName);

        String text = """
                Hi %s,

                Your VakilConnect password was just changed, and you have been signed out
                everywhere.

                If this was you, there is nothing to do.

                If it was NOT you, someone may have access to your email account. Reset your
                password again immediately and secure your mailbox.

                - VakilConnect
                """.formatted(name);

        String html = """
                <p>Hi %s,</p>
                <p>Your VakilConnect password was just changed, and you have been signed out
                everywhere.</p>
                <p>If this was you, there is nothing to do.</p>
                <p><strong>If it was not you</strong>, someone may have access to your email
                account. Reset your password again immediately and secure your mailbox.</p>
                <p>— VakilConnect</p>
                """.formatted(escape(name));

        return new EmailMessage(recipientEmail, "Your VakilConnect password was changed",
                html, text, CHANGED_TAG);
    }

    private String resetLink(String rawToken) {
        String base = properties.publicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + RESET_PATH + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String greetingName(String fullName) {
        return (fullName == null || fullName.isBlank()) ? "there" : fullName;
    }

    /**
     * fullName is registration input, interpolated into an HTML body. Without
     * escaping, a name containing markup would be injected into the email.
     */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
