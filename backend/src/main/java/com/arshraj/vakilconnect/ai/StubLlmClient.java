package com.arshraj.vakilconnect.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Answers without a model. Development and tests only.
 *
 * WHY THIS IS A REAL BEAN AND NOT A MOCKITO MOCK. Every integration test in this
 * suite builds the full application context, so something must satisfy an
 * {@code LlmClient} dependency. A mock would have to be declared in each test
 * that needed one, would drift from the interface as it grows, and - the part
 * that actually matters - would leave a context with NO LlmClient as a
 * perfectly valid configuration, so a future wiring mistake would surface as an
 * unsatisfied dependency in CI rather than as a deliberate choice. A real
 * implementation selected by property means the question "which client is
 * active" always has an answer, and the answer is assertable.
 *
 * SELECTED BY PROPERTY, NOT PROFILE, and it is the DEFAULT - exactly the pattern
 * ConsoleEmailSender established, for the same three reasons: this project has
 * no `dev` profile, so a profile-gated bean would simply not exist on a
 * developer's laptop and the Ollama adapter would be chosen instead; the default
 * must be the one that always works, and this one needs no server running, no
 * model pulled and no gigabytes of disk; and @ConditionalOnProperty is already
 * the convention here (OpenApiConfig, EmailTokenPurgeJob, both email senders).
 *
 * THE DEFAULT MATTERS MORE HERE THAN IT DID FOR THE PAID PROVIDER IT REPLACED.
 * Ollama costs nothing, so the risk is no longer an unexpected bill - it is that
 * a contributor who has never installed Ollama, or CI, would otherwise get an
 * application that starts and then fails on first use. Defaulting to the stub
 * means cloning the repository and running it works with no AI setup at all.
 *
 * NOTE ON THE DIFFERENCE FROM EMAIL. application-prod.yaml PINS
 * `vakilconnect.email.provider=resend`, because the console sender is actively
 * dangerous in production - it would write every real user's verification link
 * into the application log. This provider is pinned to nothing, so PRODUCTION
 * RUNS THE STUB. That asymmetry is deliberate and, with local inference, close
 * to forced: Render does not run Ollama, and it is not going to. Ollama is a
 * local development and demo tool - a container with a multi-gigabyte model and
 * an inference workload is not something to put on a small web instance
 * casually. Production therefore stays in stub mode until there is an explicit
 * decision about how to host the AI demo, and at AI-0 that costs nothing because
 * no endpoint, job or service calls an LlmClient at all.
 *
 * DOES NOT ECHO THE PROMPT, and that is a security property rather than a
 * convenience. An echoing stub would put user content into a return value that
 * a caller might log, assert on, or render - and the whole point of redacting
 * LlmRequest.toString() is to keep prompts out of exactly those places.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.ai.provider",
        havingValue = AiProperties.STUB, matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(StubLlmClient.class);

    /**
     * Deliberately unmistakable. If this string ever reaches a user, the
     * provider was misconfigured, and the text says so rather than looking like
     * a plausible answer - a stub that returns realistic prose is a stub that
     * gets shipped by accident.
     */
    public static final String STUB_TEXT_PREFIX =
            "[stub-llm] No model was called. Run Ollama locally and set "
                    + "vakilconnect.ai.provider=ollama. Operation: ";

    /** Reported as the model, so {@code LlmResponse.model} is never misleading. */
    public static final String STUB_MODEL = "stub";

    private final AiMetrics metrics;

    public StubLlmClient(AiMetrics metrics) {
        this.metrics = metrics;

        log.warn("AI provider is STUB - no model is called and no AI output is real. "
                + "Start Ollama and set vakilconnect.ai.provider=ollama for a live local model.");
    }

    @Override
    public String providerName() {
        return AiProperties.STUB;
    }

    /**
     * DETERMINISTIC, and never fails. A stub that threw or varied would make
     * every test that touches it flaky for reasons unrelated to what it was
     * testing.
     *
     * Metrics are recorded exactly as the real client records them, so the
     * meters exist and are assertable without a network call - which is what
     * lets AiMetrics be covered by tests at all.
     */
    @Override
    public LlmResponse complete(LlmRequest request) {
        long startedAt = System.nanoTime();

        LlmResponse response =
                new LlmResponse(STUB_TEXT_PREFIX + request.operation(), STUB_MODEL);

        metrics.recordSuccess(providerName(), request.operation());
        metrics.recordDuration(providerName(), request.operation(),
                Duration.ofNanos(System.nanoTime() - startedAt));

        return response;
    }
}
