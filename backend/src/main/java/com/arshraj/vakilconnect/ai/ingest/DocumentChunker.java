package com.arshraj.vakilconnect.ai.ingest;

import java.util.List;

/**
 * Splits normalized document text into overlapping, embeddable chunks.
 *
 * The contract every implementation must honour, because the persistence layer
 * and the idempotency guarantee both depend on it:
 *
 *   - DETERMINISTIC. The same text always produces the same chunks in the same
 *     order. Reprocessing an unchanged document must not change a single hash,
 *     or the delete-and-reinsert strategy would churn the table pointlessly and
 *     "did this document change" would be unanswerable.
 *   - INDEXES ARE 0-BASED, DENSE AND ASCENDING. `chunk_index` is half of a
 *     unique constraint and the ordering used for retrieval.
 *   - NO EMPTY CHUNKS. An empty chunk embeds to noise and pollutes retrieval;
 *     the database rejects one anyway.
 *   - THE INPUT IS NEVER MODIFIED. Strings are immutable, but the intent is
 *     stronger: the stored document's bytes are the record, and nothing in the
 *     pipeline writes back to them.
 */
public interface DocumentChunker {

    /**
     * @param text normalized document text
     * @return chunks in order; empty only if the text yields nothing usable
     */
    List<TextChunk> chunk(String text);
}
