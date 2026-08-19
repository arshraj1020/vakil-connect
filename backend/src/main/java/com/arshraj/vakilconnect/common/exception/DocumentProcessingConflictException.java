package com.arshraj.vakilconnect.common.exception;

/**
 * The document is already being processed. Maps to HTTP 409 with code
 * DOCUMENT_ALREADY_PROCESSING.
 *
 * THIS IS THE CONCURRENCY GUARD SURFACING, not an error in the usual sense. The
 * ingestion service claims a document with a conditional UPDATE whose WHERE
 * clause excludes PROCESSING; when that matches zero rows, another request got
 * there first. Returning 409 rather than silently starting a second run is what
 * stops two pipelines embedding the same document into the same chunk_index
 * values.
 *
 * 409 CONFLICT is exactly right: the request conflicts with the resource's
 * current state, and the client's remedy is to wait and poll `status` rather
 * than to change anything about the request.
 *
 * KNOWN LIMITATION, STATED HONESTLY. Because ingestion is synchronous, a
 * document can only be stuck in PROCESSING if the JVM died mid-run. Nothing
 * sweeps that state back to FAILED yet, so recovery is manual. A staleness
 * sweep belongs with asynchronous processing, which is a later decision.
 */
public class DocumentProcessingConflictException extends RuntimeException {

    public static final String CODE = "DOCUMENT_ALREADY_PROCESSING";

    public DocumentProcessingConflictException() {
        super("This document is already being processed. Check its status shortly.");
    }
}
