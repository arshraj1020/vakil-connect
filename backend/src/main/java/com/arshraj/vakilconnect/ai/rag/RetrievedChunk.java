package com.arshraj.vakilconnect.ai.rag;

import java.util.UUID;

/**
 * One chunk the vector search returned, with everything a citation needs.
 *
 * CARRIES ITS OWN PROVENANCE, and that is the point. The document id, name and
 * chunk index travel WITH the text from the moment the database returns it, so
 * the citation attached to an answer is the row that was actually retrieved -
 * never something reconstructed later, and never anything the model said.
 *
 * @param distance cosine distance from the query. 0 is identical direction, 1
 *                 orthogonal. Kept so ordering is inspectable and so a test can
 *                 assert ranking rather than merely membership.
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentName,
        int chunkIndex,
        String content,
        double distance) {

    /**
     * REDACTED. `content` is a passage of the user's legal document, so a
     * generated toString() would put it into any log line that formatted a list
     * of these - and retrieval results are exactly the sort of thing somebody
     * logs while debugging.
     */
    @Override
    public String toString() {
        return "RetrievedChunk{document=" + documentId + ", chunkIndex=" + chunkIndex
                + ", chars=" + (content == null ? 0 : content.length())
                + ", distance=" + String.format(java.util.Locale.ROOT, "%.4f", distance)
                + ", content=<not shown>}";
    }
}
