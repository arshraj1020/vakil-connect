package com.arshraj.vakilconnect.ai.analysis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Counters for document analysis as a whole.
 *
 * SEPARATE FROM AiMetrics for the reason RagMetrics is: AiMetrics measures ONE
 * INFERENCE CALL, and an analysis is a pipeline - load, bound, generate, parse.
 * Its most interesting failure is one AiMetrics cannot see at all, because it is
 * a SUCCESSFUL model call whose OUTPUT was unusable.
 *
 * `invalid_output` IS THE NUMBER WORTH WATCHING. It is the rate at which the
 * configured model fails to produce the required JSON, and it is the honest
 * measure of whether a given local model is good enough for this feature. A
 * rising value after a model change is the signal to change back.
 *
 * ONE TAG, `outcome`, with three fixed values. No user id, no document id, no
 * filename, no field names, no content. Tag values become time series, so an
 * unbounded one degrades the registry and a personal one copies user data into a
 * store with a different access-control model from the database.
 */
@Component
public class AnalysisMetrics {

    static final String REQUEST_COUNTER = "vakilconnect.ai.analysis.request";
    static final String REQUEST_TIMER = "vakilconnect.ai.analysis.duration";

    /** A structured analysis was returned. */
    static final String OUTCOME_SUCCESS = "success";

    /** The model answered, but the reply was not usable structured output. */
    static final String OUTCOME_INVALID_OUTPUT = "invalid_output";

    /** The model could not be reached, or returned nothing. */
    static final String OUTCOME_LLM_FAILURE = "llm_failure";

    private final MeterRegistry registry;

    public AnalysisMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess() {
        increment(OUTCOME_SUCCESS);
    }

    public void recordInvalidOutput() {
        increment(OUTCOME_INVALID_OUTPUT);
    }

    public void recordLlmFailure() {
        increment(OUTCOME_LLM_FAILURE);
    }

    /**
     * End-to-end latency, tagged by outcome.
     *
     * Tagging matters here: a rejected reply still paid for the whole
     * generation, so pooling it with successes would hide that failures are
     * just as slow and just as expensive as answers.
     */
    public void recordDuration(String outcome, Duration duration) {
        Timer.builder(REQUEST_TIMER)
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    private void increment(String outcome) {
        Counter.builder(REQUEST_COUNTER)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
