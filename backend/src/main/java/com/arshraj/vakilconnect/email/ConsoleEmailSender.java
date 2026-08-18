package com.arshraj.vakilconnect.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Prints the email instead of sending it. Local development only.
 *
 * THE POINT IS THE FULL BODY. A verification link is a single-use secret that
 * normally exists only in the user's inbox; printing it here is what makes the
 * flow testable locally without a real mailbox or a real provider account. That
 * is also precisely why this must never run in production - the link for every
 * real user would land in the application log.
 *
 * SELECTED BY PROPERTY, NOT PROFILE. `vakilconnect.email.provider` defaults to
 * `console` in application.yaml and is pinned to `resend` in
 * application-prod.yaml. Property-driven selection is used here rather than
 * @Profile for three reasons:
 *
 *   1. This project has no `dev` profile - local runs activate none at all - so
 *      a @Profile("dev") bean would simply never exist locally, and the
 *      Resend adapter would be selected on a developer's laptop instead.
 *   2. The default is the SAFE one. Absent configuration yields the sender that
 *      cannot mail a real person.
 *   3. It matches the existing convention: EmailTokenPurgeJob and OpenApiConfig
 *      already gate beans with @ConditionalOnProperty.
 *
 * NOTE ON matchIfMissing. It is belt-and-braces and, in practice, unreachable:
 * EmailProperties.provider is @NotBlank, so a completely absent property fails
 * validation before any condition is evaluated. It is kept because it states
 * the intent - console is the fallback - and costs nothing.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.email.provider",
        havingValue = EmailProperties.CONSOLE, matchIfMissing = true)
public class ConsoleEmailSender implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    private final EmailMetrics metrics;

    public ConsoleEmailSender(EmailMetrics metrics) {
        this.metrics = metrics;

        log.warn("Email provider is CONSOLE - messages are logged, not sent. "
                + "Set vakilconnect.email.provider=resend to deliver mail.");
    }

    @Override
    public void send(EmailMessage message) {
        /*
         * Deliberately one multi-line block rather than several statements, so
         * the whole email stays contiguous in the console even when other
         * threads are logging.
         */
        log.info("""

                ========================= EMAIL (not sent) =========================
                tag     : {}
                to      : {}
                subject : {}
                --------------------------------------------------------------------
                {}
                ====================================================================
                """,
                message.tag(),
                message.to(),
                message.subject(),
                message.text() != null ? message.text() : message.html());

        // Counted like a real send so the metric behaves identically in every
        // environment and a dashboard built locally still works in production.
        metrics.recordSent(message.tag());
    }
}
