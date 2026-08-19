package com.arshraj.vakilconnect.ai.rag;

import com.arshraj.vakilconnect.ai.embedding.Embedding;

import java.util.List;
import java.util.UUID;

/**
 * Finds the chunks most similar to a query, WITHIN ONE USER'S DOCUMENTS.
 *
 * THE OWNER IS A PARAMETER, NOT A FILTER APPLIED AFTERWARDS. Implementations
 * must push ownership into the query itself so another user's rows are never
 * loaded at all. "Retrieve globally, then filter in Java" is the same mechanism
 * with one more chance to get it wrong, and it is the shape that produces
 * cross-tenant leaks when a branch is refactored.
 *
 * NO CALLER WRITES VECTOR SQL. This interface exists so the `<=>` operator, the
 * ownership join and the distance threshold live in exactly one place. A
 * service that assembled its own query would be free to omit the join.
 */
public interface DocumentRetriever {

    /**
     * @param ownerId       the AUTHENTICATED user's id, resolved from the
     *                      security context - never from a request payload
     * @param queryEmbedding the embedded question
     * @return chunks ordered by ascending distance (most similar first), at
     *         most {@code topK}, all within the distance threshold. Empty when
     *         nothing is relevant enough - which the caller must treat as
     *         "insufficient evidence", not as an error.
     */
    List<RetrievedChunk> retrieve(UUID ownerId, Embedding queryEmbedding);
}
