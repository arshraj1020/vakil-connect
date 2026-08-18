package com.arshraj.vakilconnect.ai.document.dto;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Full metadata for one document. The body of {@code GET /api/ai/documents/{id}}.
 *
 * THERE IS NO CONTENT FIELD, AND THAT IS THE POINT. A document is up to several
 * megabytes; base64 in a metadata response would inflate every "what is this
 * file called" request into a full download the caller never asked for, and put
 * a copy of a user's legal document into any proxy log or browser cache that
 * touched the response. AI-1 exposes NO endpoint that returns bytes at all.
 *
 * A RECORD, unlike the older DTOs in this codebase (AppointmentResponse and
 * friends are mutable classes with getters and setters). The AI package
 * established records in AI-0 and there is no reason to regress: these are
 * immutable value objects, and being records is also what lets them be
 * constructed directly by a JPQL constructor expression - which is the
 * mechanism that guarantees the content column is never selected. See
 * AiDocumentRepository.
 *
 * NO REDACTING toString() HERE, deliberately, in contrast to LlmRequest. Every
 * component is metadata the owner already has; none of it is a secret or the
 * document's contents.
 */
public record DocumentResponse(

        UUID id,

        /** Sanitised at upload. Never the raw client-supplied string. */
        String filename,

        /** The server's own conclusion from the bytes, not the client's claim. */
        String contentType,

        long sizeBytes,

        /**
         * Hex SHA-256 of the stored bytes.
         *
         * Returned so a client can verify that what the server holds is what it
         * sent, without downloading it back. It discloses nothing: the owner
         * supplied the file and can compute this themselves.
         */
        String sha256,

        AiDocumentStatus status,

        /**
         * Populated only in the FAILED state; omitted from the JSON entirely
         * otherwise.
         *
         * NON_NULL matches the convention ErrorResponse.code established -
         * Jackson serialises nulls by default, and a permanent
         * {@code "failureReason": null} on every healthy document is noise that
         * invites a client to branch on it.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String failureReason,

        Instant createdAt,

        Instant updatedAt
) {
}
