package com.arshraj.vakilconnect.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Request, failure and latency counters for model calls.
 *
 * THREE MEASUREMENTS, NOT THIRTY. Token counts, cost estimates, cache hit rates
 * and per-model breakdowns are all things this project will eventually want, and
 * none of them can be interpreted until there is traffic to interpret. What is
 * needed on day one is the ability to answer "is it working, how often does it
 * fail, and how slow is it" - which is a counter, a counter and a timer.
 *
 * MeterRegistry DIRECTLY, NOT MeterBinder - the same reasoning as
 * {@code EmailMetrics}. ReferenceMigrationMetrics is a MeterBinder because it
 * SURFACES values that already exist elsewhere. Model calls are events: there is
 * no pre-existing number to bind, you increment at the moment of success or
 * failure, so a MeterBinder cannot express them.
 *
 * NAMES USE DOTS. Micrometer translates `vakilconnect.ai.request` into
 * `vakilconnect_ai_request_total` at the Prometheus scrape. Registering the
 * underscored name in Java would produce a doubled suffix and break the
 * convention every other meter in this codebase follows.
 *
 * TAG SAFETY IS THE ONE THING THAT MUST NOT BE GOT WRONG HERE. Both tags are
 * bounded and developer-chosen: `provider` is one of two literals, `operation`
 * is a fixed label such as "smoke-test". Neither may ever carry a user id, an
 * email address, a document name or - worst of all - any part of a prompt. Tag
 * values become time series, so an unbounded tag is a cardinality explosion that
 * degrades the whole registry, and a personal one copies user data into a store
 * with a completely different access-control model from the database.
 */
@Component
public class AiMetrics {

    static final String REQUEST_COUNTER = "vakilconnect.ai.request";
    static final String REQUEST_TIMER = "vakilconnect.ai.request.duration";

    static final String OUTCOME_SUCCESS = "success";
    static final String OUTCOME_FAILURE = "failure";

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** The model answered and the answer was usable. */
    public void recordSuccess(String provider, String operation) {
        increment(provider, operation, OUTCOME_SUCCESS);
    }

    /**
     * The call failed - transport, status, or a suppressed completion.
     *
     * Transient and permanent failures share one counter deliberately. Splitting
     * them would be a third tag value carrying information that is already in
     * the logs, and at AI-0 the question a dashboard needs to answer is "is the
     * failure rate moving", not "which kind".
     */
    public void recordFailure(String provider, String operation) {
        increment(provider, operation, OUTCOME_FAILURE);
    }

    /**
     * End-to-end latency of one call.
     *
     * RECORDED FOR FAILURES TOO, not only successes. The most important latency
     * signal an LLM integration has is a read timeout, and a timer that only
     * observes successes cannot see one - the calls that took longest would be
     * exactly the ones excluded, and the graph would look healthiest at the
     * moment the provider stopped responding.
     */
    public void recordDuration(String provider, String operation, Duration duration) {
        Timer.builder(REQUEST_TIMER)
                .tag("provider", provider)
                .tag("operation", operation)
                .register(registry)
                .record(duration);
    }

    private void increment(String provider, String operation, String outcome) {
        Counter.builder(REQUEST_COUNTER)
                .tag("provider", provider)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
