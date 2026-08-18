package com.arshraj.vakilconnect.ai;

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
 * The Ollama adapter, exercised WITHOUT A RUNNING OLLAMA SERVER.
 *
 * This matters more here than it did for a hosted provider. A test that talked
 * to real Ollama would pass on a laptop with the right model pulled and fail in
 * CI, or worse, pass slowly and nondeterministically - the exact failure mode
 * that makes a suite untrustworthy. MockRestServiceServer is bound to the
 * RestClient.Builder the adapter's client is built from, so every request is
 * intercepted in-process: no server, no model, no gigabytes of download, and no
 * new test dependency, since MockRestServiceServer ships in spring-test.
 *
 * NO SPRING CONTEXT IS NEEDED, unlike ResendEmailSenderTest, which has to build
 * one because @Retryable is proxy-based. This adapter carries no retry
 * annotation - AI-0 deliberately leaves the retry decision to the eventual call
 * site - so a plain constructor is the honest way to exercise it.
 */
@DisplayName("OllamaLlmClient")
class OllamaLlmClientTest {

    private static final String BASE_URL = "http://localhost:11434";
    private static final String MODEL = "llama3.2";
    private static final String ENDPOINT = BASE_URL + "/api/chat";

    private MockRestServiceServer server;
    private MeterRegistry registry;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() {
        // Fresh builder per test, bound BEFORE the client is built - so the
        // adapter's RestClient carries the mock request factory and no request
        // can escape to a real server.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        registry = new SimpleMeterRegistry();
        client = new OllamaLlmClient(builder.build(), properties(BASE_URL),
                new AiMetrics(registry));
    }

    private static AiProperties properties(String baseUrl) {
        return new AiProperties(AiProperties.OLLAMA, baseUrl, MODEL,
                0.2d, 1024, Duration.ofSeconds(5), Duration.ofSeconds(120));
    }

    private static LlmRequest request() {
        return LlmRequest.of("smoke-test", "You are a helpful assistant.",
                "What is a writ petition?");
    }

    private static String completion(String text) {
        return """
                {
                  "model": "llama3.2",
                  "created_at": "2026-08-18T00:00:00Z",
                  "message": { "role": "assistant", "content": "%s" },
                  "done": true,
                  "done_reason": "stop"
                }
                """.formatted(text);
    }

    private double count(String outcome) {
        var counter = registry.find(AiMetrics.REQUEST_COUNTER)
                .tag("provider", AiProperties.OLLAMA)
                .tag("operation", "smoke-test")
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0d : counter.count();
    }

    // ----------------------------------------------------------- happy path

    @Test
    @DisplayName("POSTs the documented /api/chat payload")
    void sendsCorrectRequest() {
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content")
                        .value("You are a helpful assistant."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content")
                        .value("What is a writ petition?"))
                .andExpect(jsonPath("$.options.temperature").value(0.2))
                .andExpect(jsonPath("$.options.num_predict").value(1024))
                .andRespond(withSuccess(completion("A writ petition is..."),
                        MediaType.APPLICATION_JSON));

        LlmResponse response = client.complete(request());

        server.verify();
        assertEquals("A writ petition is...", response.text());
        assertEquals(1d, count(AiMetrics.OUTCOME_SUCCESS));
    }

    @Test
    @DisplayName("ALWAYS sends stream:false — Ollama streams by default")
    void disablesStreaming() {
        /*
         * The single easiest way to break this integration. Ollama's default is
         * to stream newline-delimited JSON, one object per token, which is not
         * parseable as a single JSON object. Omitting this flag would produce a
         * mysterious deserialisation error rather than anything that points at
         * the cause, so it gets its own test.
         */
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess(completion("ok"), MediaType.APPLICATION_JSON));

        client.complete(request());

        server.verify();
    }

    @Test
    @DisplayName("no Authorization header is sent — local inference authenticates nothing")
    void sendsNoCredential() {
        // The requirement that motivated the provider change, asserted against
        // the real outgoing request rather than against configuration.
        server.expect(requestTo(ENDPOINT))
                .andExpect(actual -> {
                    assertFalse(actual.getHeaders().containsKey("Authorization"),
                            "Ollama needs no credential; nothing should be sent");
                    assertFalse(actual.getHeaders().containsKey("x-api-key"));
                    assertNull(actual.getURI().getQuery(),
                            "no key may ever be smuggled into the query string");
                })
                .andRespond(withSuccess(completion("ok"), MediaType.APPLICATION_JSON));

        client.complete(request());

        server.verify();
    }

    @Test
    @DisplayName("reports the model the server actually used")
    void reportsResolvedModel() {
        // `llama3.2` resolves to whichever build is pulled locally, so when
        // output quality changes with no config change, this is the evidence.
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(completion("ok"), MediaType.APPLICATION_JSON));

        assertEquals(MODEL, client.complete(request()).model());
    }

    @Test
    @DisplayName("omits the system turn entirely when there is no system prompt")
    void omitsAbsentSystemMessage() {
        // Some models treat an empty system turn as a real instruction to say
        // nothing, so an absent one must not become a blank one.
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1]").doesNotExist())
                .andRespond(withSuccess(completion("hi"), MediaType.APPLICATION_JSON));

        client.complete(LlmRequest.of("smoke-test", "hello"));

        server.verify();
    }

    @Test
    @DisplayName("records latency")
    void recordsDuration() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(completion("ok"), MediaType.APPLICATION_JSON));

        client.complete(request());

        assertNotNull(registry.find(AiMetrics.REQUEST_TIMER)
                .tag("provider", AiProperties.OLLAMA)
                .tag("operation", "smoke-test")
                .timer());
    }

    // ---------------------------------------------------------- URL handling

    @Test
    @DisplayName("endpoint is composed from the configured base URL")
    void endpointUsesConfiguredBaseUrl() {
        assertEquals(ENDPOINT, OllamaLlmClient.endpointFor(BASE_URL));
        assertEquals("http://ollama.internal:9999/api/chat",
                OllamaLlmClient.endpointFor("http://ollama.internal:9999"));
    }

    @Test
    @DisplayName("a custom port and host are honoured on the wire")
    void customBaseUrlIsUsed() {
        // Proves the property is actually plumbed through, not just stored -
        // a developer running Ollama on a non-default port must not silently
        // hit 11434.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer custom = MockRestServiceServer.bindTo(builder).build();
        OllamaLlmClient other = new OllamaLlmClient(builder.build(),
                properties("http://127.0.0.1:9999"),
                new AiMetrics(new SimpleMeterRegistry()));

        custom.expect(ExpectedCount.once(), requestTo("http://127.0.0.1:9999/api/chat"))
                .andRespond(withSuccess(completion("ok"), MediaType.APPLICATION_JSON));

        other.complete(request());

        custom.verify();
    }

    @Test
    @DisplayName("a base URL that is not a URL refuses to construct")
    void malformedBaseUrlFailsFast() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        AiMetrics metrics = new AiMetrics(new SimpleMeterRegistry());

        // No scheme at all.
        assertThrows(IllegalStateException.class,
                () -> new OllamaLlmClient(rest, properties("not-a-url"), metrics));
        // Parses, but "localhost" is read as the scheme and there is no host.
        assertThrows(IllegalStateException.class,
                () -> new OllamaLlmClient(rest, properties("localhost:11434"), metrics));
    }

    // ------------------------------------------------- transient vs permanent

    @Test
    @DisplayName("404 names the model AND the exact command to fix it")
    void missingModelIsPermanentAndActionable() {
        /*
         * The second most common failure after "Ollama is not running", and the
         * one whose raw message helps least. Ollama answers 404 when the model
         * tag has not been pulled. Naming the pull command turns a confusing
         * 404 into an instruction.
         *
         * The model name comes from OUR OWN CONFIGURATION, not from the
         * response body - the body is never read, because a provider error body
         * can echo the request, and the request is the user's prompt.
         */
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"model 'llama3.2' not found, try pulling it\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        LlmException thrown = assertThrows(PermanentLlmException.class,
                () -> client.complete(request()));

        assertTrue(thrown.getMessage().contains("ollama pull " + MODEL));
        assertFalse(thrown.isRetryable());
        assertEquals(1d, count(AiMetrics.OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("500 is transient")
    void serverErrorIsTransient() {
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        LlmException thrown = assertThrows(LlmException.class, () -> client.complete(request()));

        assertFalse(thrown instanceof PermanentLlmException);
        assertTrue(thrown.isRetryable());
        assertEquals(1d, count(AiMetrics.OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("400 is permanent")
    void badRequestIsPermanent() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(PermanentLlmException.class, () -> client.complete(request()));
    }

    @Test
    @DisplayName("a failure message never quotes the response body")
    void failureMessageLeaksNothing() {
        // A provider error body commonly echoes the offending request, and the
        // request is user content. Quoting it would put it in every log that
        // records the stack trace.
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"bad input: What is a writ petition?\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        LlmException thrown = assertThrows(PermanentLlmException.class,
                () -> client.complete(request()));

        assertTrue(thrown.getMessage().contains("400"));
        assertFalse(thrown.getMessage().contains("writ petition"));
    }

    @Test
    @DisplayName("retryable-status classification")
    void statusClassification() {
        assertTrue(OllamaLlmClient.isRetryableStatus(500));
        assertTrue(OllamaLlmClient.isRetryableStatus(503));
        assertTrue(OllamaLlmClient.isRetryableStatus(429));
        assertFalse(OllamaLlmClient.isRetryableStatus(400));
        assertFalse(OllamaLlmClient.isRetryableStatus(404));
    }

    // ------------------------------------------------------- malformed bodies

    @Test
    @DisplayName("an error reported inside a 200 body is still a failure")
    void errorInsideSuccessBodyIsPermanent() {
        // Ollama sometimes reports a problem in the body rather than the status
        // - a model that failed to load, for instance. A client that only
        // checked the status would hand back a hollow success.
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"error\":\"failed to load model\"}",
                        MediaType.APPLICATION_JSON));

        assertThrows(PermanentLlmException.class, () -> client.complete(request()));
        assertEquals(1d, count(AiMetrics.OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("an empty completion names done_reason")
    void emptyCompletionReportsDoneReason() {
        // Reached when num_predict was consumed before a token was emitted.
        // done_reason is a fixed category, not user content, so naming it is
        // safe and is the only thing that makes this debuggable.
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"\"},\"done_reason\":\"length\"}",
                        MediaType.APPLICATION_JSON));

        LlmException thrown = assertThrows(PermanentLlmException.class,
                () -> client.complete(request()));

        assertTrue(thrown.getMessage().contains("length"));
    }

    @Test
    @DisplayName("an unrecognised response shape fails cleanly rather than throwing NPE")
    void unknownShapeIsHandled() {
        // path() rather than get() at every hop: a response we did not expect
        // must produce a described exception, not a NullPointerException from
        // three frames deep.
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThrows(PermanentLlmException.class, () -> client.complete(request()));
    }

    @Test
    @DisplayName("a streamed (NDJSON) response is rejected, NOT silently truncated")
    void streamedResponseIsRejected() {
        /*
         * THE SUBTLEST FAILURE THIS ADAPTER CAN HAVE, and the reason parse()
         * checks `done` rather than trusting the status.
         *
         * A regression on `stream: false` produces newline-delimited JSON, one
         * object per token. The instinct is that Jackson would reject the
         * trailing content - IT DOES NOT. FAIL_ON_TRAILING_TOKENS is disabled by
         * default, so the converter parses the FIRST object and discards the
         * rest. Without the done=false guard this call would return a
         * one-character answer as a perfectly successful response: silent
         * truncation, invisible to every status code, exception and metric.
         *
         * Note the body below is exactly what that looks like on the wire, and
         * the first chunk carries content "A" - which is what a caller would
         * otherwise have received as the model's complete answer.
         */
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"message":{"content":"A"},"done":false}
                        {"message":{"content":"B"},"done":true}
                        """, MediaType.APPLICATION_JSON));

        LlmException thrown = assertThrows(PermanentLlmException.class,
                () -> client.complete(request()));

        assertTrue(thrown.getMessage().contains("stream"),
                "the message must point at the cause: " + thrown.getMessage());
        assertEquals(1d, count(AiMetrics.OUTCOME_FAILURE));
        assertEquals(0d, count(AiMetrics.OUTCOME_SUCCESS));
    }

    @Test
    @DisplayName("a 200 that is not JSON at all becomes a tracked LlmException")
    void unreadableBodyIsATrackedFailure() {
        /*
         * A proxy or captive-portal HTML page arrives with a success status, so
         * onStatus never fires and the failure happens in the message converter
         * instead. Without the RestClientException catch clause that escapes as
         * a raw RestClientException - breaking the exception contract LlmClient
         * documents and skipping the failure counter, so a broken integration
         * reads as NO TRAFFIC on the dashboard rather than as a fault.
         */
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("<html>proxy error</html>", MediaType.TEXT_HTML));

        LlmException thrown = assertThrows(LlmException.class, () -> client.complete(request()));

        assertEquals(1d, count(AiMetrics.OUTCOME_FAILURE));
        // The underlying exception's own message embeds the response body, which
        // is why only the exception CLASS NAME is quoted.
        assertFalse(thrown.getMessage().contains("proxy error"),
                "the unreadable body must never reach the exception message");
    }

    @Test
    @DisplayName("providerName is the low-cardinality tag value, not a class name")
    void providerNameIsStable() {
        assertEquals(AiProperties.OLLAMA, client.providerName());
    }
}
