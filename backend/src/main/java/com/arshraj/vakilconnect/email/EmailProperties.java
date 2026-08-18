package com.arshraj.vakilconnect.email;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email transport configuration.
 *
 * Bound the same way as IdentityProperties: a validated record, registered
 * explicitly on the application class, with DEFAULTS LIVING IN
 * application.yaml rather than as @DefaultValue here - one visible,
 * environment-overridable source per key.
 *
 * WHY apiKey AND from ARE NOT @NotBlank. They are only required when the
 * provider is `resend`, and a blank value is perfectly correct for the console
 * sender. Making them mandatory here would stop every developer's application
 * from starting. The fail-fast check therefore lives in
 * {@link ResendEmailSender}'s constructor, which only exists when
 * provider=resend - so the requirement is enforced exactly where and when it
 * applies.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.email")
public record EmailProperties(

        /*
         * `console` or `resend`. @NotBlank because an empty provider would
         * silently produce a context with NO EmailService bean at all, and the
         * failure would surface later as an unsatisfied dependency rather than
         * as the configuration mistake it is.
         */
        @NotBlank
        String provider,

        /** Resend API key. Required only when provider=resend. */
        String apiKey,

        /** Sender address; must be on the domain verified with the provider. */
        String from,

        /** Optional display name. */
        String fromName
) {

    public static final String CONSOLE = "console";
    public static final String RESEND = "resend";

    /**
     * REDACTED, AND THIS IS THE WHOLE POINT.
     *
     * A record's generated toString() prints EVERY component, so the default
     * would put the Resend API key into any log line, stack trace or debugger
     * frame that touched this object. Actuator's /env and /configprops are not
     * exposed, which closes the other common leak path - this closes the one
     * that remains.
     */
    @Override
    public String toString() {
        return "EmailProperties{provider=" + provider
                + ", from=" + from
                + ", fromName=" + fromName
                + ", apiKey=<redacted>}";
    }
}
