package com.arshraj.vakilconnect.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends email through the Resend HTTP API.
 *
 * THE ONLY CLASS THAT KNOWS RESEND EXISTS. Nothing else imports RestClient,
 * reads the API key, or knows the endpoint shape. Swapping providers is a new
 * class plus a property value.
 *
 * NO VENDOR SDK. RestClient ships with spring-boot-starter-web, the API is one
 * POST, and an SDK would add a dependency, a release cadence and a transitive
 * tree for no benefit.
 *
 * FAIL-FAST CONFIGURATION. This bean only exists when provider=resend, so its
 * constructor is exactly the right place to demand an API key and a from
 * address. Starting up without them would mean an application that looks
 * healthy and silently drops every email - the worst available failure mode.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.email.provider",
        havingValue = EmailProperties.RESEND)
public class ResendEmailSender implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private static final String RESEND_BASE_URL = "https://api.resend.com";
    private static final String SEND_PATH = "/emails";

    private final RestClient restClient;
    private final EmailProperties properties;
    private final EmailMetrics metrics;

    /**
     * @param builder injected rather than created so tests can bind a
     *                MockRestServiceServer to it and exercise this class
     *                without ever reaching the real Resend API.
     */
    public ResendEmailSender(RestClient.Builder builder,
                             EmailProperties properties,
                             EmailMetrics metrics) {

        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "vakilconnect.email.provider=resend requires RESEND_API_KEY. "
                            + "Refusing to start rather than silently dropping every email.");
        }
        if (properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException(
                    "vakilconnect.email.provider=resend requires EMAIL_FROM. "
                            + "Refusing to start rather than silently dropping every email.");
        }

        this.properties = properties;
        this.metrics = metrics;
        this.restClient = builder.baseUrl(RESEND_BASE_URL).build();

        // Provider and sender only. NEVER the key.
        log.info("Email provider is RESEND, sending as {}", properties.from());
    }

    /**
     * THREE ATTEMPTS, EXPONENTIAL BACKOFF, RETRYABLE FAILURES ONLY.
     *
     * `@Retryable` lives here rather than on the listener deliberately: both it
     * and `@Async` are proxy-based, and stacking them on one method makes the
     * interception order implicit. With them on separate beans each proxy wraps
     * exactly one concern, and the retries run on the async thread - never on a
     * request thread.
     *
     * The predicate is what matters. Only EmailSendException with
     * retryable=true is retried; a 4xx validation error propagates on the first
     * attempt because it would fail identically forever and retrying it just
     * burns provider quota and delays the failure signal.
     */
    @Override
    @Retryable(
            retryFor = EmailSendException.class,
            noRetryFor = PermanentEmailSendException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1_000, multiplier = 2.0, maxDelay = 8_000)
    )
    public void send(EmailMessage message) {
        long startedAt = System.nanoTime();

        try {
            restClient.post()
                    .uri(SEND_PATH)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(message))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int status = response.getStatusCode().value();
                        // Body is NOT read into the exception: a provider error
                        // body can echo the request, which includes the email
                        // body, which includes the link.
                        String detail = "Resend returned HTTP " + status;
                        throw isRetryableStatus(status)
                                ? new EmailSendException(detail)
                                : new PermanentEmailSendException(detail);
                    })
                    .toBodilessEntity();

            metrics.recordSent(message.tag());
            metrics.recordDuration(message.tag(),
                    Duration.ofNanos(System.nanoTime() - startedAt));

            log.debug("Sent {} email via Resend", message.tag());

        } catch (ResourceAccessException e) {
            // Connect/read timeout, DNS, TLS. Always worth another attempt.
            throw new EmailSendException("Resend unreachable: " + e.getMessage(), e);
        }
    }

    /**
     * 5xx and 429 are transient; every other 4xx is permanent.
     *
     * 429 is included because rate limiting is precisely the case backoff was
     * designed for. 401/403 are excluded even though they are "our fault" -
     * retrying a bad API key three times just triples the noise.
     */
    static boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }

    /**
     * Terminal handler once attempts are exhausted, or on a permanent failure.
     *
     * Does NOT rethrow. The caller is an async listener whose transaction has
     * already committed; propagating would achieve nothing except an ugly stack
     * trace on a pool thread. The counter is what makes this observable - so
     * "email silently vanished" is never a possible state.
     *
     * Logs the tag and the provider's status text only. Never the recipient,
     * never the body, never the key.
     */
    @Recover
    public void recover(EmailSendException e, EmailMessage message) {
        metrics.recordFailure(message.tag());
        log.error("Giving up on {} email after retries: {}", message.tag(), e.getMessage());
    }

    /**
     * Resend's documented JSON shape. LinkedHashMap for a stable field order,
     * which makes the request assertable in tests.
     */
    private Map<String, Object> payload(EmailMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", senderHeader());
        body.put("to", new String[]{ message.to() });
        body.put("subject", message.subject());
        if (message.html() != null && !message.html().isBlank()) {
            body.put("html", message.html());
        }
        if (message.text() != null && !message.text().isBlank()) {
            body.put("text", message.text());
        }
        return body;
    }

    private String senderHeader() {
        return (properties.fromName() == null || properties.fromName().isBlank())
                ? properties.from()
                : properties.fromName() + " <" + properties.from() + ">";
    }
}
