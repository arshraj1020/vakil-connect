package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;

import java.util.UUID;

/**
 * What one ingestion run produced. The body of
 * {@code POST /api/ai/documents/{id}/process}.
 *
 * COUNTS AND STATE ONLY - NO TEXT. Not a chunk, not a preview, not the first
 * hundred characters. The caller uploaded the document and already has it; the
 * only thing this endpoint adds is whether indexing worked, and returning
 * fragments would put document content into an HTTP response, a proxy log and a
 * browser cache for no benefit.
 *
 * NO EMBEDDINGS EITHER. A 768-float vector per chunk would be a megabyte of
 * numbers meaningless to any client, and AI-2 has no retrieval endpoint that
 * would consume them.
 */
public record IngestionResult(

        UUID documentId,

        /** READY on success. A failure throws rather than returning FAILED here. */
        AiDocumentStatus status,

        /** How many chunks were stored. Zero is impossible - empty extraction throws. */
        int chunkCount,

        /**
         * Total characters across all chunks.
         *
         * Exceeds the document's own length because chunks overlap, which is
         * the intended behaviour rather than a bug - useful when reasoning
         * about how much text was actually indexed.
         */
        int totalCharacters,

        /**
         * The embedding model that ran, and its width.
         *
         * Returned because "which model indexed this" becomes the first
         * question the moment retrieval quality changes, and because a client
         * seeing `stub-embed` knows immediately that the answers will be
         * meaningless.
         */
        String embeddingModel,

        int embeddingDimension
) {
}
