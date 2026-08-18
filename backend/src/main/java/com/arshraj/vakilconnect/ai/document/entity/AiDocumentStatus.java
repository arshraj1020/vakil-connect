package com.arshraj.vakilconnect.ai.document.entity;

/**
 * Where a document is in the ingestion lifecycle.
 *
 * MIRRORED BY A CHECK CONSTRAINT in V8 (`ck_ai_documents_status`). Adding a
 * value here without a migration makes the application refuse the insert at
 * runtime, which is the intended outcome: the set of states is schema, not an
 * implementation detail one class can widen on its own.
 *
 * AI-1 ONLY EVER PRODUCES {@link #PENDING}. Nothing extracts text yet, so no
 * code path moves a document out of it. The other three exist now rather than
 * later because they are the states the CHECK constraint has to permit, and
 * adding an enum value is free while altering a CHECK on a populated table is a
 * migration.
 */
public enum AiDocumentStatus {

    /**
     * Stored and awaiting processing. Every upload lands here.
     *
     * NOT "READY". A document whose text has never been extracted cannot answer
     * a question, and labelling it ready at upload would make the flag mean
     * "the bytes arrived" - which `created_at` already says, and which the
     * retrieval phase would then have to distrust.
     */
    PENDING,

    /**
     * Claimed by a worker and being extracted (AI-2).
     *
     * Distinct from PENDING so a crashed worker leaves evidence: a row stuck in
     * PROCESSING is a job that started and never finished, which needs
     * requeueing, whereas one stuck in PENDING was simply never picked up.
     * Collapsing the two would hide that difference.
     */
    PROCESSING,

    /** Text extracted and indexed. Usable for retrieval. */
    READY,

    /**
     * Processing failed permanently. {@code failureReason} says why.
     *
     * A terminal state, not a retry queue: something about this file did not
     * work, and re-attempting on a schedule would burn CPU on a document that
     * fails identically every time.
     */
    FAILED
}
