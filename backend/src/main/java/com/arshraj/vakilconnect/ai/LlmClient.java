package com.arshraj.vakilconnect.ai;

/**
 * Sends one prompt to a large language model and returns its text. The provider
 * is an implementation detail.
 *
 * THIS INTERFACE IS THE ENTIRE DELIVERABLE OF AI-0. Everything built later -
 * retrieval, chat, intake, summarisation - depends on this type and never on
 * Ollama, never on an HTTP client, never on a vendor SDK. Swapping providers, or
 * introducing a framework such as LangChain4j when its document splitters and
 * embedding stores are actually needed, then costs one new class and a property
 * value rather than a refactor of every caller.
 *
 * THE PROVIDER BEHIND THIS INTERFACE COSTS NOTHING TO RUN. The real
 * implementation is local inference via Ollama, so the whole feature is
 * demonstrable with no API key, no billing account and no paid service. If a
 * hosted provider is ever wanted - for a deployed demo, or for answer quality -
 * this abstraction is what makes it an additive change: a new class, a property
 * value, and not one caller touched.
 *
 * ONE METHOD, ON PURPOSE - the same discipline as {@code EmailService}. Every
 * implementation is trivially substitutable, and the stub used by the test suite
 * is a complete implementation rather than a mock with holes in it.
 *
 * NO STREAMING, NO TOOL CALLING, NO STRUCTURED OUTPUT. Those are real needs, but
 * none of them has a caller yet, and an interface designed for callers that do
 * not exist is designed against guesses. They are added when the phase that
 * needs them arrives and can say what shape they must be.
 *
 * IMPLEMENTATIONS MUST NEVER MAKE AUTHORIZATION DECISIONS. An LlmClient produces
 * text. Whether a user may see a lawyer, book an appointment or read a document
 * is decided by Spring Security and the service layer, on structured data, every
 * time - never by a model, and never by parsing a model's answer.
 *
 * Implementations are selected by {@code vakilconnect.ai.provider} - see
 * {@link StubLlmClient} and {@link OllamaLlmClient}. Exactly one is ever present
 * in a running context.
 */
public interface LlmClient {

    /**
     * Completes the prompt, or throws.
     *
     * @throws LlmException on a transient failure - a 5xx, a 429, a timeout.
     *         Another attempt could plausibly succeed.
     * @throws PermanentLlmException on a failure that will recur identically:
     *         a malformed request, a rejected key, or a response the provider
     *         suppressed for safety. Retrying only burns quota.
     */
    LlmResponse complete(LlmRequest request);

    /**
     * Short, stable, LOW-CARDINALITY identifier for this provider, used as a
     * metric tag and as the thing tests assert on.
     *
     * Tests assert on this rather than on {@code getClass()} because a bean can
     * legitimately be an AOP proxy, and a proxy's class name is not the
     * implementation's. Asserting behaviour survives that; asserting a class
     * name does not.
     */
    String providerName();
}
