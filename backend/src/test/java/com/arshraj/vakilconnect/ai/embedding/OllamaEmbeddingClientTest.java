package com.arshraj.vakilconnect.ai.embedding;

import com.arshraj.vakilconnect.ai.AiMetrics;
import com.arshraj.vakilconnect.ai.AiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Ollama embedding adapter, exercised WITHOUT A RUNNING OLLAMA SERVER.
 *
 * MockRestServiceServer is bound to the builder the client is constructed from,
 * so every request is intercepted in-process: no server, no model download, no
 * network, no API key. A test that talked to real Ollama would pass on a laptop
 * with nomic-embed-text pulled and fail in CI, which is the worst failure mode
 * available.
 */
@DisplayName("OllamaEmbeddingClient")
class OllamaEmbeddingClientTest {

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
    private static final String OUTCOME_FAILURE = "failure";

    private static final String BASE_URL = "http://localhost:11434";
    private static final String ENDPOINT = BASE_URL + "/api/embeddings";
    private static final String MODEL = "nomic-embed-text";

    private MockRestServiceServer server;
    private MeterRegistry registry;
    private OllamaEmbeddingClient client;

    private static AiProperties aiProperties() {
        return new AiProperties(AiProperties.OLLAMA, BASE_URL, "llama3.2",
                0.2d, 1024, Duration.ofSeconds(5), Duration.ofSeconds(120));
    }

    private static AiEmbeddingProperties embeddingProperties(int dimension) {
        return new AiEmbeddingProperties(AiEmbeddingProperties.OLLAMA, MODEL, dimension);
    }

    /** A well-formed Ollama response carrying exactly {@code count} values. */
    private static String embeddingResponse(int count) {
        String values = IntStream.range(0, count)
                .mapToObj(i -> String.valueOf(i / 1000.0))
                .collect(Collectors.joining(","));
        return "{\"embedding\":[" + values + "]}";
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        registry = new SimpleMeterRegistry();

        client = new OllamaEmbeddingClient(builder.build(), aiProperties(),
                embeddingProperties(DIMENSION), new AiMetrics(registry));
    }

    private double count(String outcome) {
        var counter = registry.find(REQUEST_COUNTER)
                .tag("provider", AiEmbeddingProperties.OLLAMA)
                .tag("operation", OllamaEmbeddingClient.EMBED_OPERATION)
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0d : counter.count();
    }

    // ----------------------------------------------------------- happy path

    @Test
    @DisplayName("POSTs the documented /api/embeddings payload and parses 768 values")
    void embedsSuccessfully() {
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.prompt").value("Clause 3. Security Deposit"))
                .andRespond(withSuccess(embeddingResponse(DIMENSION), MediaType.APPLICATION_JSON));

        Embedding embedding = client.embed("Clause 3. Security Deposit");

        server.verify();
        assertEquals(DIMENSION, embedding.dimension());
        assertEquals(MODEL, embedding.model());
        assertEquals(1d, count(OUTCOME_SUCCESS));
    }

    @Test
    @DisplayName("NO credential is sent - local inference authenticates nothing")
    void sendsNoCredential() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(actual -> {
                    assertFalse(actual.getHeaders().containsKey("Authorization"));
                    assertFalse(actual.getHeaders().containsKey("x-api-key"));
                    assertNull(actual.getURI().getQuery(),
                            "no key may ever be smuggled into the query string");
                })
                .andRespond(withSuccess(embeddingResponse(DIMENSION), MediaType.APPLICATION_JSON));

        client.embed("text");
        server.verify();
    }

    @Test
    @DisplayName("embedAll issues one call per text, in order")
    void embedAllIssuesOneCallEach() {
        // Ollama's /api/embeddings takes a single prompt, so batching is a loop.
        server.expect(ExpectedCount.times(3), requestTo(ENDPOINT))
                .andRespond(withSuccess(embeddingResponse(DIMENSION), MediaType.APPLICATION_JSON));

        List<Embedding> embeddings = client.embedAll(List.of("a", "b", "c"));

        server.verify();
        assertEquals(3, embeddings.size());
    }

    @Test
    @DisplayName("embedAll STOPS at the first failure - no partial result set")
    void embedAllFailsFast() {
        /*
         * The pipeline needs all-or-nothing. A document with 2 of its 3 chunks
         * embedded is worse than one with none: retrieval would silently miss
         * the last third and nothing would say so.
         */
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andRespond(withSuccess(embeddingResponse(DIMENSION), MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andRespond(withServerError());

        assertThrows(EmbeddingException.class, () -> client.embedAll(List.of("a", "b", "c")));

        // once() + verify() IS the assertion that the third call never happened.
        server.verify();
    }

    @Test
    @DisplayName("latency is recorded for successes AND for failures")
    void recordsLatencyBothWays() {
        /*
         * RECORDED FOR FAILURES TOO, which is the half that is easy to omit and
         * the half that matters most. The strongest latency signal a local model
         * gives is a read timeout, and a timer observing only successes would
         * exclude exactly the slowest calls - the graph would look healthiest at
         * the moment inference stopped working.
         */
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andRespond(withSuccess(embeddingResponse(DIMENSION), MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andRespond(withServerError());

        client.embed("first");
        assertThrows(EmbeddingException.class, () -> client.embed("second"));

        var timer = registry.find(REQUEST_TIMER)
                .tag("provider", AiEmbeddingProperties.OLLAMA)
                .tag("operation", OllamaEmbeddingClient.EMBED_OPERATION)
                .timer();

        assertNotNull(timer, "no timer published under " + REQUEST_TIMER);
        assertEquals(2L, timer.count(), "both the success and the failure must be timed");
    }

    // ------------------------------------------------- malformed responses

    @Test
    @DisplayName("A WRONG DIMENSION IS REJECTED, naming both numbers")
    void rejectsWrongDimension() {
        /*
         * THE MOST IMPORTANT TEST HERE. vector(768) rejects any other width, one
         * row at a time, midway through a batch, as a constraint error naming a
         * column. Catching it at the client turns that into "the configured
         * model does not produce the configured dimension", raised on the first
         * chunk before anything is written.
         */
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(embeddingResponse(1024), MediaType.APPLICATION_JSON));

        PermanentEmbeddingException thrown = assertThrows(PermanentEmbeddingException.class,
                () -> client.embed("text"));

        assertTrue(thrown.getMessage().contains("1024"));
        assertTrue(thrown.getMessage().contains("768"));
        assertFalse(thrown.isRetryable(), "the same model returns the same width forever");
        assertEquals(1d, count(OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("an EMPTY embedding array is rejected")
    void rejectsEmptyEmbedding() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"embedding\":[]}", MediaType.APPLICATION_JSON));

        PermanentEmbeddingException thrown = assertThrows(PermanentEmbeddingException.class,
                () -> client.embed("text"));

        // Names the likely cause: a chat model was configured for embeddings.
        assertTrue(thrown.getMessage().contains(MODEL));
    }

    @Test
    @DisplayName("a response with no embedding field is rejected")
    void rejectsMissingEmbeddingField() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThrows(PermanentEmbeddingException.class, () -> client.embed("text"));
    }

    @Test
    @DisplayName("a non-numeric value in the vector is rejected")
    void rejectsNonNumericValue() {
        String body = "{\"embedding\":[0.1,\"not-a-number\"]}";
        OllamaEmbeddingClient narrow = new OllamaEmbeddingClient(
                clientBoundTo(body), aiProperties(), embeddingProperties(2),
                new AiMetrics(new SimpleMeterRegistry()));

        assertThrows(PermanentEmbeddingException.class, () -> narrow.embed("text"));
    }

    @Test
    @DisplayName("an error reported inside a 200 body is still a failure")
    void rejectsErrorInsideSuccessBody() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"error\":\"model not loaded\"}",
                        MediaType.APPLICATION_JSON));

        assertThrows(PermanentEmbeddingException.class, () -> client.embed("text"));
    }

    @Test
    @DisplayName("a 200 that is not JSON becomes a tracked EmbeddingException")
    void rejectsUnreadableBody() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("<html>proxy error</html>", MediaType.TEXT_HTML));

        EmbeddingException thrown = assertThrows(EmbeddingException.class,
                () -> client.embed("text"));

        assertEquals(1d, count(OUTCOME_FAILURE));
        assertFalse(thrown.getMessage().contains("proxy error"),
                "the unreadable body must never reach the exception message");
    }

    // ------------------------------------------------- status classification

    @Test
    @DisplayName("404 names the model AND the pull command")
    void missingModelIsActionable() {
        // The second most common failure after "Ollama is not running".
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        PermanentEmbeddingException thrown = assertThrows(PermanentEmbeddingException.class,
                () -> client.embed("text"));

        assertTrue(thrown.getMessage().contains("ollama pull " + MODEL));
    }

    @Test
    @DisplayName("500 and 429 are transient; other 4xx are permanent")
    void statusClassification() {
        assertTrue(OllamaEmbeddingClient.isRetryableStatus(500));
        assertTrue(OllamaEmbeddingClient.isRetryableStatus(503));
        assertTrue(OllamaEmbeddingClient.isRetryableStatus(429));
        assertFalse(OllamaEmbeddingClient.isRetryableStatus(400));
        assertFalse(OllamaEmbeddingClient.isRetryableStatus(404));
    }

    @Test
    @DisplayName("a 500 is transient and counted")
    void serverErrorIsTransient() {
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        EmbeddingException thrown = assertThrows(EmbeddingException.class,
                () -> client.embed("text"));

        assertFalse(thrown instanceof PermanentEmbeddingException);
        assertTrue(thrown.isRetryable());
        assertEquals(1d, count(OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("the failure message never quotes the text being embedded")
    void neverLeaksTheEmbeddedText() {
        // The input is a passage of the user's legal document.
        String secret = "SETTLEMENT-AMOUNT-4700000";
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"bad prompt: " + secret + "\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        EmbeddingException thrown = assertThrows(EmbeddingException.class,
                () -> client.embed(secret));

        assertFalse(thrown.getMessage().contains(secret));
    }

    @Test
    @DisplayName("blank text never reaches the wire")
    void blankTextIsRefusedLocally() {
        // No server expectation is registered, so any request would fail the
        // verify below - which is the assertion.
        assertThrows(PermanentEmbeddingException.class, () -> client.embed("  "));
        server.verify();
    }

    @Test
    @DisplayName("endpoint is composed from the configured base URL")
    void endpointUsesBaseUrl() {
        assertEquals(ENDPOINT, OllamaEmbeddingClient.endpointFor(BASE_URL));
        assertEquals("http://127.0.0.1:9999/api/embeddings",
                OllamaEmbeddingClient.endpointFor("http://127.0.0.1:9999"));
    }

    /** A client whose single response is fixed, for one-off shape tests. */
    private RestClient clientBoundTo(String jsonBody) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(jsonBody, MediaType.APPLICATION_JSON));
        return builder.build();
    }
}
