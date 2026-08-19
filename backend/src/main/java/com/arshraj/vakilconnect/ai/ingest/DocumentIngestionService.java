package com.arshraj.vakilconnect.ai.ingest;

import java.util.UUID;

/**
 * Turns a stored document into embedded chunks.
 *
 * TAKES THE CALLER'S EMAIL FIRST, like every other service here. The value
 * comes from {@code Authentication.getName()}, set by JwtAuthenticationFilter
 * from a verified token signature - never from a request body or a header. A
 * method taking a userId a caller could supply would be an authorization bypass
 * with a pleasant signature.
 *
 * IDEMPOTENT BY CONSTRUCTION. Running it twice on an unchanged document
 * produces byte-identical chunks in the same order: extraction, normalization
 * and chunking are all deterministic, and stage 3 replaces rather than appends.
 * That is what makes "retry after a failure" a plain re-POST.
 */
public interface DocumentIngestionService {

    /**
     * Extracts, chunks and embeds one document the caller owns.
     *
     * @throws com.arshraj.vakilconnect.common.exception.ResourceNotFoundException
     *         if no such document exists OR it belongs to another user - the two
     *         are deliberately indistinguishable
     * @throws com.arshraj.vakilconnect.common.exception.DocumentProcessingConflictException
     *         if another run already holds the document
     * @throws com.arshraj.vakilconnect.common.exception.DocumentExtractionException
     *         if no text can be read from the file
     * @throws com.arshraj.vakilconnect.common.exception.DocumentEmbeddingException
     *         if embeddings cannot be generated
     */
    IngestionResult process(String userEmail, UUID documentId);
}
