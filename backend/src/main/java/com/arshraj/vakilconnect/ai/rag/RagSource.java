package com.arshraj.vakilconnect.ai.rag;

import java.util.UUID;

/**
 * One citation. Structured, not a string parsed out of the answer.
 *
 * EVERY INSTANCE IS BUILT FROM A ROW THE DATABASE RETURNED. The service maps
 * retrieval results into these directly; nothing reads the model's prose to
 * discover which documents it "used". That distinction is the whole citation
 * design: a model can be talked into claiming any source, but it cannot add a
 * row to a list it never touches.
 *
 * @param excerpt a short, TRUNCATED preview so a user can see why a source was
 *                cited without opening the document. Deliberately not the whole
 *                chunk - a full chunk per source would make the response mostly
 *                document text, which is what the document endpoint is for.
 */
public record RagSource(
        UUID documentId,
        String documentName,
        int chunkIndex,
        String excerpt) {

    /** Characters of chunk text shown per citation. */
    private static final int EXCERPT_LENGTH = 240;

    static RagSource from(RetrievedChunk chunk) {
        String content = chunk.content().strip();
        String excerpt = content.length() <= EXCERPT_LENGTH
                ? content
                : content.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";

        return new RagSource(chunk.documentId(), chunk.documentName(),
                chunk.chunkIndex(), excerpt);
    }
}
