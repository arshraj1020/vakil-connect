package com.arshraj.vakilconnect.ai.document.dto;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The body of a successful {@code POST /api/ai/documents}, returned with 201.
 *
 * WHY THIS EXISTS RATHER THAN REUSING {@link DocumentResponse}. The two would
 * be identical today, and that is exactly the trap. An upload response answers
 * "what did you just create, and what happens next" - the id to poll and the
 * state it started in. A metadata response answers "what is this document". As
 * AI-2 adds processing detail those diverge, and having merged them would mean
 * either leaking half-meaningful fields into a creation response or splitting
 * them later as a breaking change.
 *
 * THE ECHOED FILENAME IS THE SANITISED ONE. A client that uploaded
 * `../../etc/passwd` gets back `passwd`, which is also what will appear in
 * their list - so the response is the first place the caller learns their name
 * was normalised, rather than being surprised by it later.
 *
 * NO CONTENT FIELD. Echoing the bytes back to the sender would double the cost
 * of every upload to restate something the client already has.
 */
public record DocumentUploadResponse(

        UUID id,

        /** SANITISED. May differ from what the client sent - deliberately. */
        String filename,

        /** The server's own conclusion from the bytes, not the client's claim. */
        String contentType,

        long sizeBytes,

        /**
         * Hex SHA-256 of what was stored, so the client can confirm the upload
         * arrived intact without asking for it back.
         */
        String sha256,

        /**
         * Always {@code PENDING} at AI-1 - nothing extracts text yet.
         *
         * Returned rather than assumed so the client polls the field instead of
         * hardcoding the state, which is what makes AI-2's asynchronous
         * processing an additive change on the frontend rather than a rewrite.
         */
        AiDocumentStatus status,

        Instant createdAt
) {
}
