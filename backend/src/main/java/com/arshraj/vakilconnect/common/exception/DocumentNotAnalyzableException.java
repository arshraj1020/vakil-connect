package com.arshraj.vakilconnect.common.exception;

/**
 * The document exists and belongs to the caller, but is not in a state where it
 * can be analysed. Maps to HTTP 409 with code DOCUMENT_NOT_ANALYZABLE.
 *
 * 409 CONFLICT, matching DocumentProcessingConflictException: the request
 * conflicts with the resource's CURRENT STATE, and the remedy is to change the
 * resource rather than the request. A 400 would blame a request that was
 * perfectly well formed, and a 404 would be a lie - the caller owns this
 * document and can see it in their list.
 *
 * A DISTINCT CODE FROM DOCUMENT_ALREADY_PROCESSING, because the client action is
 * distinct. "Already processing" means wait and poll; this means CALL
 * {@code POST /api/ai/documents/{id}/process} FIRST. Collapsing them would leave
 * a frontend polling forever on a document that will never leave PENDING on its
 * own.
 *
 * THE MESSAGES ARE FIXED CONSTANTS ON THIS CLASS. They are returned to the
 * client and written to logs, so they must never carry a filename, a parser
 * message, or anything derived from document content - the same rule
 * DocumentIngestionServiceImpl's failure reasons follow. Naming them here makes
 * that checkable rather than a discipline somebody has to remember at each throw
 * site.
 */
public class DocumentNotAnalyzableException extends RuntimeException {

    public static final String CODE = "DOCUMENT_NOT_ANALYZABLE";

    /** PENDING or FAILED: AI-2's pipeline has not produced usable text. */
    public static final String NOT_PROCESSED =
            "This document has not been processed yet. Process it before requesting an analysis.";

    /**
     * READY but with no chunks.
     *
     * Should be unreachable - ingestion marks a document FAILED rather than
     * READY when chunking yields nothing - so this covers a row that was left
     * inconsistent by an older run or a manual edit. Analysing an empty context
     * and returning the result would be far worse than saying so.
     */
    public static final String NO_TEXT =
            "This document has no indexed text to analyse. Process it again.";

    public DocumentNotAnalyzableException(String message) {
        super(message);
    }
}
