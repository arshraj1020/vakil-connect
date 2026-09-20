package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;

import java.util.List;
import java.util.UUID;

/**
 * One document, loaded for analysis: its identity, its state, and its indexed
 * text.
 *
 * A DETACHED VALUE OBJECT, not an entity. AnalysisDocumentLoader builds it
 * inside a read transaction and hands it back; everything downstream - the
 * context builder, the prompt, the model call, which together take tens of
 * seconds - works on plain strings with no persistence context, no lazy proxy
 * and no database connection anywhere near them.
 *
 * @param documentId the primary key, from the database. This is the value that
 *                   reaches the response; nothing the model says can replace it.
 * @param documentName the sanitised filename stored at upload, from the
 *                   database. Also never the model's.
 * @param status     read so the service can refuse a document that has not been
 *                   through AI-2's pipeline, rather than analysing an empty
 *                   context and presenting the result as an analysis.
 * @param chunks     chunk text in ASCENDING chunk_index order - the document's
 *                   own order, which is what makes context selection
 *                   deterministic. Empty for any document that is not READY,
 *                   because the loader does not read text it has already
 *                   decided not to use.
 */
public record AnalysisDocument(UUID documentId,
                               String documentName,
                               AiDocumentStatus status,
                               List<String> chunks) {

    public boolean isReady() {
        return status == AiDocumentStatus.READY;
    }

    public boolean hasText() {
        return !chunks.isEmpty();
    }

    /** REDACTED. `chunks` is the whole document. */
    @Override
    public String toString() {
        return "AnalysisDocument{documentId=" + documentId
                + ", status=" + status
                + ", chunks=" + chunks.size()
                + ", content=<not shown>}";
    }
}
