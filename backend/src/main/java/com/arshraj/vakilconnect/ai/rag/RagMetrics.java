package com.arshraj.vakilconnect.ai.rag;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Counters for the RAG pipeline as a whole.
 *
 * SEPARATE FROM AiMetrics, which measures ONE INFERENCE CALL. A RAG request
 * spans an embedding call, a vector search and a completion; its interesting
 * failure modes are pipeline stages, not HTTP statuses. Merging them would make
 * "how many questions failed" and "how many model calls failed" the same
 * number, and they are not - the most common failure returns no evidence and
 * never calls a model at all.
 *
 * ONE TAG, `outcome`, with four fixed values. No user id, no email, no document
 * name, no question, no prompt, no retrieved text. Tag values become time
 * series, so an unbounded one degrades the registry and a personal one copies
 * user data into a store with a different access-control model from the
 * database.
 *
 * THE RETRIEVAL-MISS COUNTER IS THE MOST USEFUL NUMBER HERE. A rising miss rate
 * means the distance threshold is too strict, documents are not being ingested,
 * or users are asking about material they never uploaded - and it is invisible
 * in any per-call metric because those requests never reach the model.
 */
@Component
public class RagMetrics {

    static final String REQUEST_COUNTER = "vakilconnect.ai.rag.request";
    static final String REQUEST_TIMER = "vakilconnect.ai.rag.duration";
    static final String CHUNKS_SUMMARY = "vakilconnect.ai.rag.retrieved.chunks";

    static final String OUTCOME_SUCCESS = "success";
    static final String OUTCOME_INSUFFICIENT = "insufficient_evidence";
    static final String OUTCOME_LLM_FAILURE = "llm_failure";
    static final String OUTCOME_FAILURE = "failure";

    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** A grounded answer was returned. */
    public void recordSuccess() {
        increment(OUTCOME_SUCCESS);
    }

    /** Retrieval found nothing relevant enough; no model call was made. */
    public void recordRetrievalMiss() {
        increment(OUTCOME_INSUFFICIENT);
    }

    /** The model was reached but could not produce a usable answer. */
    public void recordLlmFailure() {
        increment(OUTCOME_LLM_FAILURE);
    }

    /** Anything else - embedding unavailable, or a defect. */
    public void recordFailure() {
        increment(OUTCOME_FAILURE);
    }

    /**
     * How many chunks a successful retrieval produced.
     *
     * A distribution rather than a counter: the useful question is the SHAPE.
     * A median of 1 with topK at 6 says the threshold is doing most of the
     * filtering, which a running total cannot show.
     */
    public void recordRetrievalHit(int chunks) {
        DistributionSummary.builder(CHUNKS_SUMMARY)
                .description("Chunks retrieved per grounded RAG request")
                .register(registry)
                .record(chunks);
    }

    /**
     * End-to-end latency, TAGGED BY OUTCOME.
     *
     * Tagging matters here more than in the other timers: an
     * insufficient-evidence request skips the model and finishes in
     * milliseconds, while a grounded one waits on local inference for tens of
     * seconds. Pooling them would produce an average describing neither.
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
