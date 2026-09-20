package com.arshraj.vakilconnect.ai.compare;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The body of {@code POST /api/ai/documents/compare}.
 *
 * TWO IDS, UNLIKE AI-3's ask REQUEST, and the difference is real rather than
 * an inconsistency. {@code AskQuestionRequest} carries no document id because
 * the search is corpus-wide - there is nothing to name. A comparison names two
 * specific resources by construction, so the ids belong in the request the
 * same way a single id belongs in AI-4's path variable. Both ids are
 * re-verified against the caller's ownership regardless of what is supplied
 * here - see {@link DocumentComparisonServiceImpl} - so this field is a
 * selection, never an authorization.
 */
public record DocumentComparisonRequest(

        @NotNull(message = "Choose the first document to compare.")
        UUID documentId,

        @NotNull(message = "Choose the second document to compare.")
        UUID compareToDocumentId) {
}
