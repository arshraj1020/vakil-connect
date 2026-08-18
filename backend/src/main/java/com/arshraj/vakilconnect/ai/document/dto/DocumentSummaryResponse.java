package com.arshraj.vakilconnect.ai.document.dto;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /api/ai/documents}.
 *
 * NARROWER THAN {@link DocumentResponse} ON PURPOSE, rather than reusing it.
 * The list is rendered as a table: it needs a name, a size, a state and a date,
 * and nothing else. Omitting sha256 and failureReason keeps the payload
 * proportional to a list that may hold many rows, and means the detail endpoint
 * has a reason to exist.
 *
 * NO CONTENT FIELD, for the same reason as DocumentResponse - and here the cost
 * of getting it wrong would be multiplied by the number of documents the user
 * owns.
 */
public record DocumentSummaryResponse(

        UUID id,

        /** Sanitised at upload. Never the raw client-supplied string. */
        String filename,

        /** The server's own conclusion from the bytes, not the client's claim. */
        String contentType,

        long sizeBytes,

        AiDocumentStatus status,

        Instant createdAt
) {
}
