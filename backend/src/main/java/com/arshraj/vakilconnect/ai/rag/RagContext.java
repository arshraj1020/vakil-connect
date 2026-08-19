package com.arshraj.vakilconnect.ai.rag;

import java.util.List;

/**
 * The bounded evidence assembled for one question.
 *
 * @param chunks   the chunks that FIT, in retrieval order. May be shorter than
 *                 what retrieval returned, if the character budget ran out.
 * @param rendered the text block placed in the prompt, with each chunk labelled
 *                 [Source N] so the model can refer to them
 * @param truncated whether anything was dropped, so the caller can say so
 *                  rather than silently answering from partial evidence
 */
public record RagContext(List<RetrievedChunk> chunks, String rendered, boolean truncated) {

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    /**
     * REDACTED. `rendered` is several thousand characters of the user's legal
     * documents - the single largest concentration of their content anywhere in
     * this feature.
     */
    @Override
    public String toString() {
        return "RagContext{chunks=" + chunks.size() + ", chars="
                + (rendered == null ? 0 : rendered.length())
                + ", truncated=" + truncated + ", rendered=<not shown>}";
    }
}
