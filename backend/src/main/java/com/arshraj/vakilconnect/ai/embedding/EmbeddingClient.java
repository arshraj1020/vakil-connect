package com.arshraj.vakilconnect.ai.embedding;

import java.util.List;

/**
 * Turns text into vectors. The provider is an implementation detail.
 *
 * A SEPARATE INTERFACE FROM LlmClient, NOT AN EXTRA METHOD ON IT. Chat
 * completion and embedding are different operations against different models
 * with different failure modes, different latency profiles and different
 * configuration. Bolting {@code embed()} onto LlmClient would force
 * StubLlmClient and OllamaLlmClient to implement something neither has any
 * business knowing about, and would mean AI-3's retrieval code depends on the
 * chat interface in order to search.
 *
 * SAME SHAPE AS LlmClient, on purpose: one narrow interface, provider chosen by
 * property, a stub that is a complete implementation rather than a mock, and
 * exactly one bean present in a running context. Anything learned about that
 * pattern in AI-0 applies here unchanged.
 *
 * IMPLEMENTATIONS MUST NEVER MAKE AUTHORIZATION DECISIONS. An EmbeddingClient
 * turns text into numbers. Which documents a user may reach is settled by SQL,
 * before any text arrives here.
 */
public interface EmbeddingClient {

    /**
     * Embeds one piece of text.
     *
     * @throws EmbeddingException on a transient failure - 5xx, 429, a timeout
     * @throws PermanentEmbeddingException on a failure that will recur: a
     *         rejected request, a missing model, or a vector whose dimension
     *         does not match configuration
     */
    Embedding embed(String text);

    /**
     * Embeds several pieces of text, in order.
     *
     * PRESENT ON THE INTERFACE RATHER THAN LOOPED BY CALLERS because whether a
     * provider can batch is the provider's business. Ollama's /api/embeddings
     * takes one input per call, so the Ollama implementation loops - but a
     * provider that batches natively can override this and callers need not
     * change. The returned list is positionally aligned with the input.
     */
    List<Embedding> embedAll(List<String> texts);

    /**
     * The dimension this client produces.
     *
     * Load-bearing: `ai_document_chunks.embedding` is `vector(768)`, so a
     * mismatch is a constraint violation on every insert. Exposing it lets the
     * pipeline check once, up front, instead of discovering it per row.
     */
    int dimension();

    /** Low-cardinality identifier used as a metric tag and asserted by tests. */
    String providerName();
}
