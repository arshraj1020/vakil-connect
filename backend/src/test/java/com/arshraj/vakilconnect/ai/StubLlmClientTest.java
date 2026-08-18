package com.arshraj.vakilconnect.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stub, and the request/response invariants every implementation shares.
 *
 * The stub is what the entire test suite runs against, so "the stub works" is
 * load-bearing for every later AI phase rather than a formality.
 */
@DisplayName("StubLlmClient")
class StubLlmClientTest {

    private MeterRegistry registry;
    private StubLlmClient client;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        client = new StubLlmClient(new AiMetrics(registry));
    }

    @Test
    @DisplayName("returns usable text without any network call")
    void returnsText() {
        LlmResponse response = client.complete(LlmRequest.of("smoke-test", "anything"));

        assertTrue(response.text().startsWith(StubLlmClient.STUB_TEXT_PREFIX));
        assertEquals(StubLlmClient.STUB_MODEL, response.model());
    }

    @Test
    @DisplayName("is deterministic — the same request gives the same answer")
    void isDeterministic() {
        // A stub that varied would make every test that touches it flaky for
        // reasons unrelated to what it was testing.
        LlmRequest request = LlmRequest.of("smoke-test", "anything");

        assertEquals(client.complete(request).text(), client.complete(request).text());
    }

    @Test
    @DisplayName("NEVER echoes the prompt back")
    void doesNotEchoThePrompt() {
        /*
         * A security property, not a convenience. An echoing stub puts user
         * content into a return value that a caller might log, assert on or
         * render - which is precisely what redacting LlmRequest.toString() sets
         * out to prevent.
         */
        String sensitive = "my landlord is withholding my deposit";

        String text = client.complete(LlmRequest.of("intake", sensitive)).text();

        assertFalse(text.contains(sensitive));
    }

    @Test
    @DisplayName("says unmistakably that no model was called")
    void announcesItself() {
        // A stub that returned plausible prose is a stub that gets shipped by
        // accident. If this text ever reaches a user, it must say what went
        // wrong.
        String text = client.complete(LlmRequest.of("smoke-test", "anything")).text();

        assertTrue(text.contains("stub-llm"));
        assertTrue(text.contains("vakilconnect.ai.provider=ollama"));
    }

    @Test
    @DisplayName("records the same meters the real client records")
    void recordsMetrics() {
        // This is what lets AiMetrics be covered at all without a network call.
        client.complete(LlmRequest.of("smoke-test", "anything"));

        assertEquals(1d, registry.find(AiMetrics.REQUEST_COUNTER)
                .tag("provider", AiProperties.STUB)
                .tag("operation", "smoke-test")
                .tag("outcome", AiMetrics.OUTCOME_SUCCESS)
                .counter().count());

        assertNotNull(registry.find(AiMetrics.REQUEST_TIMER)
                .tag("provider", AiProperties.STUB)
                .tag("operation", "smoke-test")
                .timer());
    }

    @Test
    @DisplayName("providerName is the low-cardinality tag value")
    void providerName() {
        assertEquals(AiProperties.STUB, client.providerName());
    }

    // ----------------------------------------- shared contract invariants

    @Test
    @DisplayName("LlmRequest rejects a blank operation — it is a metric tag")
    void operationIsRequired() {
        // An empty tag value would file every call under one meaningless series
        // and make the metric useless the moment a second operation exists.
        assertThrows(IllegalArgumentException.class,
                () -> LlmRequest.of("", "prompt"));
        assertThrows(IllegalArgumentException.class,
                () -> LlmRequest.of("   ", "prompt"));
    }

    @Test
    @DisplayName("LlmRequest rejects a blank prompt")
    void promptIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmRequest.of("smoke-test", "  "));
    }

    @Test
    @DisplayName("LlmResponse rejects blank text — an empty completion is a failure")
    void responseTextIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LlmResponse("", "stub"));
    }

    @Test
    @DisplayName("LlmRequest.toString() NEVER contains the prompt")
    void requestToStringIsRedacted() {
        /*
         * THE ONLY REDACTION LEFT THAT IS LOAD-BEARING. AiProperties needs
         * none now that local inference has removed every credential from the
         * AI layer - but a prompt is user content regardless of which provider
         * answers it. A record prints every component, so one log line formatting
         * this object would publish a user's description of their legal problem
         * into logs that on Render are retained, searchable and readable by
         * anyone with dashboard access.
         */
        String system = "you are a legal assistant";
        String user = "my landlord is withholding my deposit";

        String rendered = LlmRequest.of("intake", system, user).toString();

        assertFalse(rendered.contains(user));
        assertFalse(rendered.contains(system));
        assertTrue(rendered.contains("<redacted>"));

        // Operation and lengths survive: they are what make a truncation or a
        // token-limit failure debuggable, and neither discloses anything.
        assertTrue(rendered.contains("intake"));
        assertTrue(rendered.contains("userPromptChars=" + user.length()));
    }

    @Test
    @DisplayName("LlmResponse.toString() NEVER contains the completion")
    void responseToStringIsRedacted() {
        // Model output is derived from user input and routinely quotes it back,
        // so it carries the same disclosure risk as the prompt.
        String text = "Your landlord must return the deposit within 30 days.";

        String rendered = new LlmResponse(text, "llama3.2").toString();

        assertFalse(rendered.contains(text));
        assertTrue(rendered.contains("<redacted>"));
        assertTrue(rendered.contains("llama3.2"));
    }
}
