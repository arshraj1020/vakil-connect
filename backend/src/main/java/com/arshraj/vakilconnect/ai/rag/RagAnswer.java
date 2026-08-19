package com.arshraj.vakilconnect.ai.rag;

import java.util.List;

/**
 * The body of a successful ask.
 *
 * @param grounded false when retrieval found nothing relevant enough. The
 *                 client renders that differently from an answer - and the
 *                 distinction is honest rather than cosmetic: an ungrounded
 *                 response was produced WITHOUT calling the model at all.
 * @param sources  derived from retrieval results only. Empty exactly when
 *                 {@code grounded} is false.
 * @param truncated whether the character budget dropped some retrieved chunks,
 *                 so a user seeing a thin answer knows why.
 */
public record RagAnswer(
        String answer,
        boolean grounded,
        List<RagSource> sources,
        boolean truncated) {

    /** The no-evidence outcome. No model call, no sources, nothing invented. */
    static RagAnswer insufficientEvidence() {
        return new RagAnswer(RagPromptBuilder.INSUFFICIENT_PHRASE, false, List.of(), false);
    }

    static RagAnswer grounded(String answer, List<RagSource> sources, boolean truncated) {
        return new RagAnswer(answer, true, List.copyOf(sources), truncated);
    }

    /**
     * REDACTED. The answer quotes the user's documents and the excerpts are
     * document text outright.
     */
    @Override
    public String toString() {
        return "RagAnswer{grounded=" + grounded + ", sources=" + sources.size()
                + ", answerChars=" + (answer == null ? 0 : answer.length())
                + ", truncated=" + truncated + ", content=<not shown>}";
    }
}
