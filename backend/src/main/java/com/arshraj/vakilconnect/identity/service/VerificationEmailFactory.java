package com.arshraj.vakilconnect.identity.service;

import com.arshraj.vakilconnect.email.EmailMessage;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Renders the verification email.
 *
 * LIVES IN identity/, NOT email/. This is the boundary the Phase 3 design
 * turns on: `email` owns TRANSPORT and knows nothing about verification, while
 * this class knows everything about verification and nothing about Resend,
 * retries or HTTP. It produces a finished {@link EmailMessage} and hands it
 * over. Nothing here imports a provider.
 *
 * THE LINK POINTS AT THE FRONTEND, NOT THE API. It targets a Next.js page that
 * reads the token from the query string and POSTs it. A GET must never consume
 * a token: mail scanners and link prefetchers open links before a human does,
 * and a mutating GET would let a security appliance burn the token before the
 * user ever clicks.
 *
 * Built from IdentityProperties.publicBaseUrl - the value Phase 1 already
 * introduced. No second base-URL property exists, deliberately: two sources of
 * truth for one URL is how production ends up mailing links to localhost.
 */
@Component
public class VerificationEmailFactory {

    /** Metric tag and log label. Fixed, non-sensitive, low cardinality. */
    public static final String TAG = "verification";

    /** The frontend route that handles the token. */
    private static final String VERIFY_PATH = "/verify-email";

    private final IdentityProperties properties;

    public VerificationEmailFactory(IdentityProperties properties) {
        this.properties = properties;
    }

    /**
     * @param rawToken the single-use secret. Placed in the link and NOWHERE
     *                 else - not logged here, not stored, and redacted by
     *                 EmailMessage.toString().
     */
    public EmailMessage create(String recipientEmail, String fullName, String rawToken) {
        String link = verificationLink(rawToken);
        long hours = properties.verificationTokenTtl().toHours();
        String greetingName = (fullName == null || fullName.isBlank()) ? "there" : fullName;

        String text = """
                Hi %s,

                Confirm your email address to finish setting up your VakilConnect account:

                %s

                This link expires in %d hours and can only be used once.

                If you did not create a VakilConnect account, you can ignore this email -
                no account will be activated without confirming this link.

                - VakilConnect
                """.formatted(greetingName, link, hours);

        String html = """
                <p>Hi %s,</p>
                <p>Confirm your email address to finish setting up your VakilConnect account.</p>
                <p><a href="%s">Verify my email</a></p>
                <p>Or paste this link into your browser:<br><span>%s</span></p>
                <p>This link expires in %d hours and can only be used once.</p>
                <p>If you did not create a VakilConnect account, you can ignore this email —
                no account will be activated without confirming this link.</p>
                <p>— VakilConnect</p>
                """.formatted(escape(greetingName), link, link, hours);

        return new EmailMessage(recipientEmail, "Verify your VakilConnect email", html, text, TAG);
    }

    /**
     * URL-encodes the token even though Base64-URL output contains no character
     * that needs it. Belt and braces: if the token encoding is ever changed,
     * this stops a stray '+' or '/' from silently corrupting every link.
     */
    private String verificationLink(String rawToken) {
        String base = properties.publicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + VERIFY_PATH + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    /**
     * Minimal HTML escaping for the one interpolated user-controlled value.
     *
     * fullName comes from registration input. Without this, a name containing
     * markup would be injected into the email body - and some mail clients
     * render enough HTML for that to matter.
     */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
