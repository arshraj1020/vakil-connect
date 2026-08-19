package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.embedding.Embedding;

import java.util.List;

/**
 * Stage 2's output, carried into stage 3.
 *
 * @param chunks     in ascending chunk_index order
 * @param embeddings POSITIONALLY ALIGNED with {@code chunks} - embeddings.get(i)
 *                   is the vector for chunks.get(i). ChunkEmbeddingWriter
 *                   re-checks the sizes before writing anything, because a
 *                   misalignment would pair one chunk's text with another's
 *                   vector and the only symptom would be retrieval quietly
 *                   returning the wrong passage forever.
 * @param model      the embedding model that produced them
 */
public record PreparedChunks(List<TextChunk> chunks, List<Embedding> embeddings, String model) {

    public PreparedChunks {
        if (chunks == null || embeddings == null) {
            throw new IllegalArgumentException("chunks and embeddings are required");
        }
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "chunks (" + chunks.size() + ") and embeddings (" + embeddings.size()
                            + ") must align positionally");
        }
    }

    public int totalCharacters() {
        return chunks.stream().mapToInt(TextChunk::charCount).sum();
    }

    /**
     * REDACTED. Both lists hold user content - chunk text directly, and vectors
     * derived from it. Counts are all a log line legitimately needs.
     */
    @Override
    public String toString() {
        return "PreparedChunks{chunks=" + chunks.size()
                + ", model=" + model + ", content=<not shown>}";
    }
}
