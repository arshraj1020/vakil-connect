package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.LlmResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * An LlmClient that returns whatever the test chooses and records what it was
 * asked. Duplicated per AI package rather than shared - see AI-4's own
 * ScriptedLlmClient for why: each is a small, obviously-correct fake, and each
 * lives in test sources for exactly the package that needs it.
 *
 * NOT @Component. This package sits under the application's component scan,
 * and test-classes share the classpath - annotating this would register a fake
 * model in every @SpringBootTest context in the suite. EmailDispatchIT records
 * what happens when that rule is broken.
 */
class ScriptedLlmClient implements LlmClient {

    private final List<LlmRequest> requests = new ArrayList<>();
    private String reply = ComparisonFixtures.validReply();
    private RuntimeException failure;

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

    ScriptedLlmClient replying(String text) {
        this.reply = text;
        this.failure = null;
        return this;
    }

    ScriptedLlmClient failingWith(RuntimeException e) {
        this.failure = e;
        return this;
    }

    ScriptedLlmClient reset() {
        requests.clear();
        reply = ComparisonFixtures.validReply();
        failure = null;
        return this;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);

        if (failure != null) {
            throw failure;
        }
        if (reply == null || reply.isBlank()) {
            throw new LlmException("model returned nothing");
        }
        return new LlmResponse(reply, "scripted");
    }

    @Override
    public String providerName() {
        return "scripted";
    }
}
