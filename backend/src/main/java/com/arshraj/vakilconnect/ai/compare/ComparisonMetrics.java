package com.arshraj.vakilconnect.ai.compare;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Counters for document comparison as a whole. Mirrors AI-4's AnalysisMetrics
 * exactly - see that class for why this is separate from AiMetrics (one
 * inference call) and why `invalid_output` is tracked apart from
 * `llm_failure` (model quality versus infrastructure).
 */
@Component
public class ComparisonMetrics {

    static final String REQUEST_COUNTER = "vakilconnect.ai.comparison.request";
    static final String REQUEST_TIMER = "vakilconnect.ai.comparison.duration";

    static final String OUTCOME_SUCCESS = "success";
    static final String OUTCOME_INVALID_OUTPUT = "invalid_output";
    static final String OUTCOME_LLM_FAILURE = "llm_failure";

    private final MeterRegistry registry;

    public ComparisonMetrics(MeterRegistry registry) {
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
