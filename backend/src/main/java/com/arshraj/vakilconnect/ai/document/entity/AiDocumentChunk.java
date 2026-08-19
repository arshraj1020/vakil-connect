package com.arshraj.vakilconnect.ai.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One embeddable piece of a document.
 *
 * NO user_id COLUMN, AND NO OWNER FIELD. Ownership is inherited through
 * {@link AiDocument}, which owns it through `users`. Copying the owner here
 * would create a second copy of a security-relevant fact that an UPDATE could
 * leave stale, and "which copy is authoritative" is not a question a security
 * boundary should ever raise. Every owner-scoped query joins instead.
 *
 * THE `embedding` COLUMN IS DELIBERATELY NOT MAPPED. V9 declares it
 * `vector(768)`, a type Hibernate has no mapping for - attempting one would
 * need a custom UserType for a value this application never reads back in AI-2.
 * `ddl-auto: validate` ignores columns an entity does not map, so leaving it out
 * is safe and is exactly the precedent `email_tokens` set for its unmapped
 * audit columns.
 *
 * The consequence, and it is enforced rather than hoped: THIS ENTITY CANNOT BE
 * USED TO INSERT A CHUNK. A JPA insert would omit `embedding`, which is NOT
 * NULL, and fail. Writes go through {@link
 * com.arshraj.vakilconnect.ai.ingest.ChunkEmbeddingWriter}, the one class that
 * knows the vector wire format. This entity exists for reads, counts and
 * deletes.
 */
@Entity
@Table(name = "ai_document_chunks")
public class AiDocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /*
     * LAZY. Chunk reads are already scoped by document id, so the parent is
     * known before the query runs and materialising it would be a join nobody
     * asked for. `open-in-view: false` means any access must be inside a
     * transaction regardless.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private AiDocument document;

    /**
     * 0-based position, dense and ascending.
     *
     * Half of `uq_ai_document_chunks_position`, and the ordering retrieval will
     * use in AI-3 to reassemble neighbouring passages. Stable across
     * reprocessing because chunking is deterministic.
     */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false)
    private String content;

    /** Hex SHA-256 of {@link #content}: exactly 64 characters, matching varchar(64). */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** Characters, not tokens - see V9 for why a token count would be a lie. */
    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiDocumentChunk() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AiDocument getDocument() {
        return document;
    }

    public void setDocument(AiDocument document) {
        this.document = document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * REDACTED. `content` is a passage of the user's legal document, so a
     * generated toString() would put it into any log line that formatted this
     * object. The hash is what makes two runs comparable without reproducing
     * the text.
     */
    @Override
    public String toString() {
        return "AiDocumentChunk{id=" + id
                + ", chunkIndex=" + chunkIndex
                + ", chars=" + charCount
                + ", hash=" + (contentHash == null ? "null" : contentHash.substring(0, 12) + "...")
                + ", content=<not shown>}";
    }
}
