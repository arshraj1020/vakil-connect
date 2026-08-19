package com.arshraj.vakilconnect.ai.document.repository;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads and deletes for {@code ai_document_chunks}.
 *
 * THERE IS NO INSERT HERE, AND THAT IS STRUCTURAL. `embedding` is `vector(768)`
 * and unmapped by {@link AiDocumentChunk}, so a JPA insert would omit a NOT NULL
 * column and fail. Writes belong to
 * {@link com.arshraj.vakilconnect.ai.ingest.ChunkEmbeddingWriter}, which is the
 * only class that knows pgvector's wire format. Splitting it this way means the
 * vector format has exactly one home rather than leaking into every caller.
 *
 * OWNERSHIP IS IN THE SQL, via a join to `ai_documents`. No chunk query trusts a
 * document id on its own: the owner is a predicate, so a row belonging to
 * somebody else is never loaded and there is no object in memory for a later
 * branch to forget to check. Same discipline as AiDocumentRepository.
 */
public interface AiDocumentChunkRepository extends JpaRepository<AiDocumentChunk, UUID> {

    /**
     * Deletes every chunk of one document.
     *
     * THE FIRST HALF OF IDEMPOTENT REPROCESSING: delete, then re-insert, inside
     * one transaction. Without it a second run would collide with
     * `uq_ai_document_chunks_position` on chunk 0 - correctly, but as a
     * constraint violation rather than as the clean replacement it should be.
     *
     * NOT owner-scoped, and deliberately: the caller has ALREADY resolved and
     * claimed the document by owner in stage 1, so re-checking here would be
     * ceremony. It is package-visible only to the ingestion pipeline in
     * practice, and the read methods below - which serve controllers and tests -
     * do carry the owner.
     *
     * A bulk DELETE, so the rows are never loaded into the persistence context
     * to be thrown away. clearAutomatically because a bulk delete bypasses that
     * context; flushAutomatically so pending writes are visible to it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AiDocumentChunk c WHERE c.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * One document's chunks in order, but only if the caller owns the document.
     *
     * Returns empty both when the document has no chunks AND when it belongs to
     * somebody else - indistinguishable by design, so probing ids reveals
     * nothing. The join is what makes the owner check unforgettable.
     */
    @Query("""
            SELECT c
              FROM AiDocumentChunk c
             WHERE c.document.id = :documentId
               AND c.document.user.id = :userId
             ORDER BY c.chunkIndex ASC
            """)
    List<AiDocumentChunk> findByDocumentAndOwner(@Param("documentId") UUID documentId,
                                                 @Param("userId") UUID userId);

    /**
     * How many chunks a document has. Used by the process response, and by the
     * tests that prove reprocessing replaces rather than accumulates.
     */
    long countByDocumentId(UUID documentId);
}
