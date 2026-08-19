package com.arshraj.vakilconnect.ai.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/ai/documents/ask}.
 *
 * ONE FIELD, AND NO DOCUMENT ID. The question is scoped to everything the
 * authenticated user owns, and the owner comes from the security context. A
 * documentId here would be a client-supplied identifier feeding a retrieval
 * query - which the service would have to re-verify anyway, so the field would
 * add an attack surface and no capability.
 *
 * @param question 4000 characters is far more than any real question and far
 *                 less than a context window. The bound is a control, not a
 *                 courtesy: an unbounded question is both an inference cost and
 *                 a way to push the system instructions out of a small model's
 *                 window, which is an injection vector rather than merely a
 *                 performance one. Kept in sync with
 *                 vakilconnect.ai.retrieval.max-question-characters, which the
 *                 service enforces as the authority.
 */
public record AskQuestionRequest(

        @NotBlank(message = "Ask a question about your documents.")
        @Size(max = 4000, message = "That question is too long.")
        String question) {

    /**
     * REDACTED. A question about a legal matter is user content - "can my
     * landlord keep my deposit after the eviction notice" is exactly the sort
     * of sentence that must not land in a log. A record prints every component
     * by default, and Spring logs request bodies in several failure paths.
     */
    @Override
    public String toString() {
        return "AskQuestionRequest{questionChars="
                + (question == null ? 0 : question.length()) + ", question=<redacted>}";
    }
}
