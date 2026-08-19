package com.arshraj.vakilconnect.ai.embedding;

import com.arshraj.vakilconnect.ai.AiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stub the whole suite embeds with.
 *
 * "The stub works" is load-bearing rather than a formality: every ingestion
 * test's vectors come from here, and they have to be accepted by the real
 * vector(768) column.
 */
@DisplayName("StubEmbeddingClient")
class StubEmbeddingClientTest {

    private static final int DIMENSION = 768;

    /* ---------------------------------------------------------------------
     * THE PUBLISHED METRIC CONTRACT, as literals.
     *
     * These deliberately do NOT reference AiMetrics' constants. Those are
     * package-private in com.arshraj.vakilconnect.ai, and widening them so a
     * test in a neighbouring package could read them would trade real
     * encapsulation for test convenience - the metric NAMES are a published
     * interface, but the constants holding them are an implementation detail
     * that happens to be shaped like one.
     *
     * Writing the literals is also the stronger assertion. Micrometer renders
     * `vakilconnect.ai.request` as `vakilconnect_ai_request_total` at the
     * Prometheus scrape, so these exact strings are what a dashboard query and
     * an alert rule are written against. Referencing the constant would make a
     * rename invisible to this test while silently breaking every dashboard;
     * hard-coding it means a rename fails here first and has to be a deliberate
     * decision about a public contract.
     * ------------------------------------------------------------------- */

    private static final String REQUEST_COUNTER = "vakilconnect.ai.request";
    private static final String REQUEST_TIMER = "vakilconnect.ai.request.duration";
    private static final String OUTCOME_SUCCESS = "success";


    private MeterRegistry registry;
    private StubEmbeddingClient client;

    private static AiEmbeddingProperties properties(int dimension) {
        return new AiEmbeddingProperties(
                AiEmbeddingProperties.STUB, "nomic-embed-text", dimension);
    }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        client = new StubEmbeddingClient(properties(DIMENSION), new AiMetrics(registry));
    }

    @Test
    @DisplayName("produces a vector of exactly the configured dimension")
    void producesConfiguredDimension() {
        // 768 here, not a convenient small number: the stub must produce vectors
        // the real vector(768) column accepts, or every persistence test would
        // pass against a width production never sees.
        Embedding embedding = client.embed("The Tenant shall pay Rs. 45,000 per month.");

        assertEquals(DIMENSION, embedding.dimension());
        assertEquals(DIMENSION, embedding.values().length);
        assertEquals(DIMENSION, client.dimension());
    }

    @Test
    @DisplayName("the dimension follows configuration")
    void dimensionIsConfigurable() {
        StubEmbeddingClient narrow =
                new StubEmbeddingClient(properties(384), new AiMetrics(new SimpleMeterRegistry()));

        assertEquals(384, narrow.embed("text").dimension());
    }

    @Test
    @DisplayName("DETERMINISTIC - the same text always gives the same vector")
    void isDeterministic() {
        // What lets "reprocessing produces identical chunks and vectors" be a
        // testable property rather than a hope.
        assertArrayEquals(client.embed("clause one").values(),
                client.embed("clause one").values());
    }

    @Test
    @DisplayName("different text gives a different vector")
    void differentTextDiffers() {
        assertFalse(java.util.Arrays.equals(
                client.embed("clause one").values(),
                client.embed("clause two").values()));
    }

    @Test
    @DisplayName("values sit in the range a real normalised embedding occupies")
    void valuesAreInRange() {
        // So nothing downstream is tuned against numbers it will never see again.
        for (float value : client.embed("some legal text").values()) {
            assertTrue(value >= -1f && value <= 1f, "out of [-1,1]: " + value);
        }
    }

    @Test
    @DisplayName("embedAll returns one vector per input, in order")
    void embedAllIsPositional() {
        List<String> texts = List.of("first", "second", "third");

        List<Embedding> embeddings = client.embedAll(texts);

        assertEquals(3, embeddings.size());
        // Positional alignment is what ChunkEmbeddingWriter relies on; a
        // mismatch would pair one chunk's text with another's vector.
        for (int i = 0; i < texts.size(); i++) {
            assertArrayEquals(client.embed(texts.get(i)).values(), embeddings.get(i).values());
        }
    }

    @Test
    @DisplayName("blank text is refused rather than embedded to noise")
    void refusesBlankText() {
        assertThrows(PermanentEmbeddingException.class, () -> client.embed(""));
        assertThrows(PermanentEmbeddingException.class, () -> client.embed("   "));
        assertThrows(PermanentEmbeddingException.class, () -> client.embed(null));
    }

    @Test
    @DisplayName("reports itself as the stub, never as a real model")
    void reportsStubIdentity() {
        // If these ever reach a user, the text says the answers are meaningless.
        assertEquals(AiEmbeddingProperties.STUB, client.providerName());
        assertEquals(StubEmbeddingClient.STUB_MODEL, client.embed("x").model());
    }

    @Test
    @DisplayName("records the same meters the real client records")
    void recordsMetrics() {
        client.embed("text");

        assertEquals(1d, registry.find(REQUEST_COUNTER)
                .tag("provider", AiEmbeddingProperties.STUB)
                .tag("operation", OllamaEmbeddingClient.EMBED_OPERATION)
                .tag("outcome", OUTCOME_SUCCESS)
                .counter().count());

        assertNotNull(registry.find(REQUEST_TIMER)
                .tag("provider", AiEmbeddingProperties.STUB)
                .tag("operation", OllamaEmbeddingClient.EMBED_OPERATION)
                .timer());
    }

    @Test
    @DisplayName("Embedding.toString NEVER renders the vector")
    void embeddingToStringIsSafe() {
        // A record prints every component, and float[] would render as an array
        // identity or - if someone "improved" it - thousands of numbers derived
        // from the user's document.
        String rendered = client.embed("confidential settlement terms").toString();

        assertTrue(rendered.contains("dimension=768"));
        assertTrue(rendered.contains("not shown"));
        assertFalse(rendered.contains("["));
    }

    @Test
    @DisplayName("the pgvector literal uses a dot, never a locale comma")
    void pgVectorLiteralIsLocaleSafe() {
        /*
         * A JVM with a European default locale formats floats with a decimal
         * COMMA, which would turn [0.1,0.2] into [0,1,0,2] - a syntactically
         * valid vector of twice the length and entirely wrong values. Silent,
         * and visible only as bad retrieval.
         */
        String literal = new Embedding(new float[]{ 0.5f, -0.25f }, "m").toPgVectorLiteral();

        assertTrue(literal.startsWith("[") && literal.endsWith("]"));
        assertEquals(2, literal.split(",").length, "a comma-decimal locale would give 4 parts");
        assertTrue(literal.contains("."));
    }

    @Test
    @DisplayName("an empty or blank-model Embedding cannot be constructed")
    void embeddingInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new Embedding(new float[0], "m"));
        assertThrows(IllegalArgumentException.class, () -> new Embedding(new float[]{ 1f }, " "));
    }
}
