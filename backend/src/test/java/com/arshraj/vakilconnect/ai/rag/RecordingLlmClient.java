package com.arshraj.vakilconnect.ai.rag;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.LlmResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * An LlmClient that records what it was asked and returns what the test
 * chooses.
 *
 * HAND-ROLLED RATHER THAN MOCKITO, matching this project - nothing in the suite
 * uses a mocking framework, and a recording fake reads better for the two
 * things these tests actually need: "was it called at all" (the no-evidence
 * guarantee) and "what exactly was in the prompt" (the injection defence).
 *
 * NEVER REACHES A NETWORK. No Ollama, no key, no cost.
 */
class RecordingLlmClient implements LlmClient {

    private final List<LlmRequest> requests = new ArrayList<>();
    private String answer = "The notice period is 30 days [Source 1].";
    private RuntimeException failure;

    /** Every request this client received, in order. Empty means never called. */
    List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    int callCount() {
        return requests.size();
    }

    LlmRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new AssertionError("the model was never called");
        }
        return requests.get(requests.size() - 1);
    }

    RecordingLlmClient answering(String text) {
        this.answer = text;
        return this;
    }

    RecordingLlmClient failingWith(RuntimeException e) {
        this.failure = e;
        return this;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        if (answer == null || answer.isBlank()) {
            /*
             * LlmResponse refuses blank text by construction, so a provider
             * returning nothing surfaces as an exception rather than an empty
             * response object. Reproduced faithfully here so the service's
             * empty-answer path is exercised the way it would really happen.
             */
            throw new LlmException("model returned nothing");
        }
        return new LlmResponse(answer, "stub");
    }

    @Override
    public String providerName() {
        return "recording";
    }
}
