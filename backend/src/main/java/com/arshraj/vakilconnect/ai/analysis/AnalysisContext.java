package com.arshraj.vakilconnect.ai.analysis;

/**
 * The bounded slice of one document that the model is shown.
 *
 * @param rendered   the text block placed in the prompt, chunks in document
 *                   order, each labelled so the model can see where one ends
 * @param chunkCount how many chunks were included
 * @param truncated  whether the character budget dropped any, so the response
 *                   can say the analysis covers part of the document rather
 *                   than presenting a partial reading as a complete one
 */
public record AnalysisContext(String rendered, int chunkCount, boolean truncated) {

    public boolean isEmpty() {
        return chunkCount == 0;
    }

    /**
     * REDACTED. `rendered` is up to twelve thousand characters of the user's
     * legal document - the largest single concentration of their content in
     * this feature.
     */
    @Override
    public String toString() {
        return "AnalysisContext{chunks=" + chunkCount
                + ", chars=" + (rendered == null ? 0 : rendered.length())
                + ", truncated=" + truncated + ", rendered=<not shown>}";
    }
}
