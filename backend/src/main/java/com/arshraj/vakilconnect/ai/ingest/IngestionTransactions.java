package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.document.entity.AiDocument;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentChunkRepository;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentRepository;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Every database transaction the ingestion pipeline performs, in one bean.
 *
 * ============ WHY THIS IS A SEPARATE CLASS AND NOT PRIVATE METHODS ==========
 *
 * SPRING'S @Transactional IS PROXY-BASED, SO SELF-INVOCATION SILENTLY DOES
 * NOTHING. If these methods lived on DocumentIngestionServiceImpl and were
 * called as {@code this.claim(...)}, the call would go straight to the target
 * object and never touch the transactional proxy. No transaction would be
 * started. Nothing would fail, nothing would warn - the annotations would read
 * as documentation and behave as decoration, and the atomicity that stage 3
 * depends on would not exist.
 *
 * That failure mode is invisible in tests that use a single connection and
 * catastrophic in production, which is exactly why the stages live here: a
 * different bean means a real proxy, and a real proxy means real transactions.
 *
 * THE COROLLARY IS THE PROPERTY THE PIPELINE NEEDS. DocumentIngestionServiceImpl
 * carries NO @Transactional anywhere, so the slow part of ingestion - extraction
 * and N embedding calls - provably runs with no transaction and no connection
 * held. It cannot accidentally acquire one, because there is nothing on it to
 * propagate from.
 *
 * Each method below is therefore SHORT BY CONSTRUCTION: it does database work
 * and returns. No inference, no HTTP, no file parsing.
 */
@Component
public class IngestionTransactions {

    private static final Logger log = LoggerFactory.getLogger(IngestionTransactions.class);

    private final AiDocumentRepository documentRepository;
    private final AiDocumentChunkRepository chunkRepository;
    private final ChunkEmbeddingWriter chunkWriter;

    public IngestionTransactions(AiDocumentRepository documentRepository,
                                 AiDocumentChunkRepository chunkRepository,
                                 ChunkEmbeddingWriter chunkWriter) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunkWriter = chunkWriter;
    }

    /**
     * STAGE 1. Claims the document with a conditional UPDATE.
     *
     * @return true if claimed; false if the UPDATE matched nothing
     */
    @Transactional
    public boolean claim(UUID documentId, UUID ownerId) {
        return documentRepository.claimForProcessing(documentId, ownerId, Instant.now()) == 1;
    }

    /**
     * Whether the document exists AND belongs to this user.
     *
     * Called only after a failed claim, to separate "not yours or not there"
     * from "already processing" - two outcomes that must produce different
     * statuses without either revealing anything about another user's data.
     */
    @Transactional(readOnly = true)
    public boolean isVisibleToOwner(UUID documentId, UUID ownerId) {
        return documentRepository.findMetadataByIdAndOwner(documentId, ownerId).isPresent();
    }

    /**
     * Loads the stored bytes in a short read transaction.
     *
     * The bytes are needed for the whole of stage 2; the CONNECTION is not.
     * Reading them in a transaction that ends immediately means the document
     * sits in heap while the pool slot goes back to the pool.
     */
    @Transactional(readOnly = true)
    public AiDocument loadForIngestion(UUID documentId, UUID ownerId) {
        Optional<AiDocument> document =
                documentRepository.findForIngestion(documentId, ownerId);
        return document.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    /**
     * STAGE 3. Replaces every chunk and marks the document READY, atomically.
     *
     * DELETE-THEN-INSERT, NOT UPSERT. Reprocessing after a chunk-size change
     * produces a different NUMBER of chunks, so an upsert keyed on
     * (document_id, chunk_index) would rewrite the first N and leave the tail
     * of a previous, longer run behind - stale text carrying a valid embedding,
     * indistinguishable from current content at retrieval time. Deleting first
     * makes the stored state a function of the current input alone.
     *
     * One transaction, so a failure anywhere leaves the PREVIOUS chunks intact
     * rather than a half-replaced set, and the document stays out of READY.
     */
    @Transactional
    public void replaceChunks(UUID documentId, PreparedChunks prepared, int expectedDimension) {
        chunkRepository.deleteByDocumentId(documentId);
        chunkWriter.write(documentId, prepared.chunks(), prepared.embeddings(), expectedDimension);
        documentRepository.markReady(documentId, Instant.now());
    }

    /**
     * Records a failure in its OWN transaction.
     *
     * REQUIRES_NEW IS NOT USED, AND THE REASON MATTERS: the caller holds no
     * transaction at all - DocumentIngestionServiceImpl has none by design - so
     * this simply starts a fresh one. There is no rollback-only outer
     * transaction for it to be swallowed by, which is the failure that would
     * otherwise strand a document in PROCESSING forever.
     *
     * The reason is one of the fixed constants on the service. It is returned
     * to the client and written to logs, so it must never carry a parser
     * message, an exception message, or anything derived from document content.
     */
    @Transactional
    public void markFailed(UUID documentId, String reason) {
        try {
            documentRepository.markFailed(documentId, reason, Instant.now());
        } catch (RuntimeException e) {
            // Never let bookkeeping mask the original failure.
            log.error("Could not mark document {} FAILED", documentId, e);
        }
    }
}
