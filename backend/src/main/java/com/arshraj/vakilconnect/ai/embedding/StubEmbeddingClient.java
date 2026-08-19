package com.arshraj.vakilconnect.ai.embedding;

import com.arshraj.vakilconnect.ai.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Produces vectors without a model. Development and tests only.
 *
 * WHAT MAKES THIS USEFUL RATHER THAN A PLACEHOLDER: the vectors are DERIVED
 * FROM THE TEXT, deterministically. The same chunk always embeds to the same
 * vector, and different chunks embed to different ones. That is enough to test
 * everything AI-2 actually needs to prove - that chunks are stored, that
 * reprocessing is idempotent, that a dimension mismatch is caught, that
 * ordering is stable - without a model, a download, or a running server.
 *
 * It is NOT semantically meaningful. Similar sentences do not get similar
 * vectors, so nothing here can validate retrieval QUALITY. That is correct for
 * this phase: AI-2 builds the pipeline, and judging whether the pipeline finds
 * the right clause is AI-3's problem, against a real model.
 *
 * SELECTED BY PROPERTY, AND THE DEFAULT - the pattern ConsoleEmailSender and
 * StubLlmClient established. A contributor who has never installed Ollama can
 * clone, run the suite, and exercise the whole ingestion pipeline. CI does
 * exactly that.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.ai.embedding.provider",
        havingValue = AiEmbeddingProperties.STUB, matchIfMissing = true)
public class StubEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(StubEmbeddingClient.class);

    /** Reported as the model, so an Embedding never misrepresents its origin. */
    public static final String STUB_MODEL = "stub-embed";

    private final AiEmbeddingProperties properties;
    private final AiMetrics metrics;

    public StubEmbeddingClient(AiEmbeddingProperties properties, AiMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;

        log.warn("Embedding provider is STUB - vectors are derived from text hashes and carry "
                + "NO SEMANTIC MEANING. Retrieval built on these will not work. "
                + "Set vakilconnect.ai.embedding.provider=ollama for real embeddings.");
    }

    @Override
    public String providerName() {
        return AiEmbeddingProperties.STUB;
    }

    /**
     * THE CONFIGURED DIMENSION, not a convenient small number.
     *
     * Load-bearing: the stub must produce vectors the real `vector(768)` column
     * accepts, or every persistence test would pass against a width production
     * never sees, and the first real ingestion would fail on a constraint no
     * test had ever exercised.
     */
    @Override
    public int dimension() {
        return properties.dimension();
    }

    @Override
    public List<Embedding> embedAll(List<String> texts) {
        List<Embedding> embeddings = new ArrayList<>(texts.size());
        for (String text : texts) {
            embeddings.add(embed(text));
        }
        return embeddings;
    }

    /**
     * SHA-256 of the text seeds a PRNG, which fills the vector.
     *
     * Deterministic across runs and across JVMs, because both SHA-256 and
     * java.util.Random's LCG are fully specified - so a test asserting "the
     * same document reprocesses to identical vectors" is testing the pipeline
     * rather than the weather.
     *
     * Values are in [-1, 1], the range a real normalised embedding occupies, so
     * nothing downstream is tuned against numbers it will never see again.
     *
     * NEVER THROWS. A stub that failed intermittently would make every test
     * that touches it flaky for reasons unrelated to what it was testing. The
     * failure paths are exercised against the real client with a mock server.
     */
    @Override
    public Embedding embed(String text) {
        if (text == null || text.isBlank()) {
            throw new PermanentEmbeddingException("cannot embed blank text");
        }

        long startedAt = System.nanoTime();

        Random random = new Random(seedFor(text));
        float[] values = new float[properties.dimension()];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextFloat() * 2f - 1f;
        }

        metrics.recordSuccess(providerName(), OllamaEmbeddingClient.EMBED_OPERATION);
        metrics.recordDuration(providerName(), OllamaEmbeddingClient.EMBED_OPERATION,
                Duration.ofNanos(System.nanoTime() - startedAt));

        return new Embedding(values, STUB_MODEL);
    }

    /** First eight bytes of the SHA-256, as a long. */
    private static long seedFor(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            long seed = 0L;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (digest[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            // Mandated by the JLS for every conforming JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
