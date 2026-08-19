package com.arshraj.vakilconnect.ai.ingest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Pipeline-level counters for document ingestion.
 *
 * SEPARATE FROM AiMetrics, WHICH EMBEDDING CALLS REUSE. AiMetrics measures one
 * INFERENCE CALL - provider, operation, outcome, latency - and the embedding
 * client publishes there with operation="embed". This class measures one
 * INGESTION RUN, which is a different unit: it spans extraction, chunking and N
 * embedding calls, and its failure modes are pipeline stages rather than HTTP
 * statuses. Forcing both into one set of meters would make "how many documents
 * failed" and "how many embedding calls failed" the same number, which they are
 * not.
 *
 * EVERY TAG IS A FIXED LITERAL. There is exactly one tag, `stage`, and its
 * values are the four constants below. No user id, no email, no filename, no
 * document id, no content - tag values become time series, so an unbounded one
 * degrades the whole registry and a personal one copies user data into a store
 * with a completely different access-control model from the database.
 *
 * Note what is deliberately NOT tagged: the document id. It is the obvious
 * thing to want when debugging, and it is precisely the unbounded tag that
 * would create a new time series per upload. Logs carry it; metrics must not.
 */
@Component
public class AiIngestionMetrics {

    static final String INGESTION_COUNTER = "vakilconnect.ai.ingestion";
    static final String INGESTION_TIMER = "vakilconnect.ai.ingestion.duration";
    static final String CHUNKS_SUMMARY = "vakilconnect.ai.ingestion.chunks";

    /** The run completed and the document is READY. */
    static final String STAGE_SUCCESS = "success";

    /** Text could not be read from the file - corrupt, encrypted, or scanned. */
    static final String STAGE_EXTRACTION_FAILURE = "extraction_failure";

    /** Embeddings could not be generated - usually Ollama down or model absent. */
    static final String STAGE_EMBEDDING_FAILURE = "embedding_failure";

    /** Anything else: a database error, or a defect. */
    static final String STAGE_FAILURE = "failure";

    private final MeterRegistry registry;

    public AiIngestionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess() {
        increment(STAGE_SUCCESS);
    }

    public void recordExtractionFailure() {
        increment(STAGE_EXTRACTION_FAILURE);
    }

    public void recordEmbeddingFailure() {
        increment(STAGE_EMBEDDING_FAILURE);
    }

    public void recordFailure() {
        increment(STAGE_FAILURE);
    }

    /**
     * How many chunks a document produced.
     *
     * A DistributionSummary rather than a counter, because the useful question
     * is about the SHAPE - a median of 30 with a p99 of 1800 says the chunk
     * ceiling is being hit by real uploads, which a running total cannot show.
     */
    public void recordChunkCount(int chunks) {
        DistributionSummary.builder(CHUNKS_SUMMARY)
                .description("Chunks produced per ingested document")
                .register(registry)
                .record(chunks);
    }

    /**
     * End-to-end duration of one run.
     *
     * RECORDED FOR FAILURES TOO. The most important latency signal this
     * pipeline has is a timeout against a local model, and a timer that
     * observed only successes would exclude exactly the slowest runs - the
     * graph would look healthiest at the moment ingestion stopped working.
     */
    public void recordDuration(String stage, Duration duration) {
        Timer.builder(INGESTION_TIMER)
                .tag("stage", stage)
                .register(registry)
                .record(duration);
    }

    private void increment(String stage) {
        Counter.builder(INGESTION_COUNTER)
                .tag("stage", stage)
                .register(registry)
                .increment();
    }
}
