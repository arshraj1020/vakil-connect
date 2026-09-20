package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.LlmResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * An LlmClient that returns whatever the test chooses and records what it was
 * asked.
 *
 * HAND-ROLLED RATHER THAN MOCKITO, matching AI-3's RecordingLlmClient and the
 * rest of this project. A recording fake reads better for the two things these
 * tests need: "what exactly was in the prompt" (the injection defence) and "how
 * many times was it called" (proving the failure paths short-circuit).
 *
 * USED IN TWO PLACES, WHICH IS WHY IT LIVES IN ITS OWN FILE. The unit tests
 * construct it directly; DocumentAnalysisIT registers it as a @Primary bean so
 * the HTTP path can be exercised with a reply that is real JSON. AI-3's
 * equivalent was nested and package-private because it only ever had one caller.
 *
 * NOT ANNOTATED @Component, DELIBERATELY. This class sits in a package the
 * application's component scan covers, and test-classes are on the same
 * classpath - Boot's TypeExcludeFilter skips @TestConfiguration but NOT
 * @Component, so an annotation here would inject a fake model into EVERY
 * @SpringBootTest context in the suite. EmailDispatchIT records the same trap.
 *
 * NEVER REACHES A NETWORK. No Ollama, no key, no cost.
 */
class ScriptedLlmClient implements LlmClient {

    private final List<LlmRequest> requests = new ArrayList<>();
    private String reply = AnalysisFixtures.validReply();
    private RuntimeException failure;

    /** Every request received, in order. Empty means the model was never called. */
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

    /** Back to the default valid reply, for a bean shared across an IT class. */
    ScriptedLlmClient reset() {
        requests.clear();
        reply = AnalysisFixtures.validReply();
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
            /*
             * LlmResponse refuses blank text by construction, so a provider that
             * returns nothing surfaces as an EXCEPTION rather than as an empty
             * response object. Reproduced faithfully here, so the service's
             * empty-output path is exercised the way it would really happen
             * instead of through a state the type system forbids.
             */
            throw new LlmException("model returned nothing");
        }
        return new LlmResponse(reply, "scripted");
    }

    @Override
    public String providerName() {
        return "scripted";
    }
}
