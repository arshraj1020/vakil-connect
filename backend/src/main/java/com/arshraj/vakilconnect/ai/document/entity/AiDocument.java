package com.arshraj.vakilconnect.ai.document.entity;

import com.arshraj.vakilconnect.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One uploaded file, its server-determined metadata, and its lifecycle state.
 *
 * MAPPED TO A TABLE DEFINED IN V8. Every column below was read out of that
 * migration; nothing here may drift from it, because `ddl-auto: validate` turns
 * a mismatch into a refusal to start rather than a runtime error.
 *
 * NEVER LEAVES THE SERVICE LAYER. No controller returns this type and no DTO
 * embeds it. That is not a style rule here - this object holds the full file
 * contents, so serialising it would put a base64 copy of a user's document into
 * an HTTP response that only asked for its name.
 *
 * DELIBERATELY DOES NOT EXTEND BaseEntity, for the same two reasons EmailToken
 * does not:
 *
 *   1. BaseEntity's timestamps are LocalDateTime (zone-less, JVM wall clock).
 *      V8 declares these columns `timestamptz`, so inheriting would be a type
 *      mismatch that `validate` rejects at boot.
 *   2. This entity maintains `updatedAt` itself, below, with the same
 *      @PrePersist/@PreUpdate shape - so nothing is lost by not inheriting.
 *
 * NO VECTOR COLUMN, NO EXTRACTED TEXT, NO CHUNKS. AI-1 stores the file and
 * nothing derived from it.
 */
@Entity
@Table(name = "ai_documents")
public class AiDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /*
     * LAZY. The owner is needed to WRITE a document, but every read path in
     * this feature is a projection query that never materialises this entity at
     * all - so an eager join would be pure cost on the one path that does load
     * it. `open-in-view: false` means any access must be inside a transaction
     * regardless.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The SANITISED name. Path components, control characters and anything else
     * hostile were removed at the boundary; the raw client string is never
     * stored, so nothing downstream has to remember to re-clean it.
     */
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /**
     * THE SERVER'S CONCLUSION FROM THE BYTES, not the client's claim. The
     * multipart Content-Type header never reaches this field.
     */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Hex SHA-256 of {@link #content} - exactly 64 characters, matching varchar(64). */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /**
     * The file itself.
     *
     * @JdbcTypeCode(VARBINARY) IS LOAD-BEARING AND MUST NOT BE REMOVED.
     *
     * Hibernate maps a plain {@code byte[]} to VARBINARY, which PostgreSQL
     * renders as `bytea` - what V8 declares. Annotating the field {@code @Lob}
     * instead would map it to BLOB, which on PostgreSQL is `oid`: a LARGE
     * OBJECT REFERENCE, stored out of line in pg_largeobject, requiring
     * explicit lo_unlink on delete or the bytes leak forever. It is the classic
     * Hibernate/PostgreSQL trap, and the failure is quiet - the schema
     * validates against a different column type and the data goes somewhere
     * else entirely.
     *
     * Stating the JDBC type explicitly means the mapping is a decision recorded
     * in the code rather than a default someone could change with an innocent
     * annotation.
     *
     * NOT LAZY, AND IT DOES NOT NEED TO BE. JPA's {@code @Basic(fetch = LAZY)}
     * on a byte[] is silently ignored without the Hibernate bytecode enhancer,
     * which this build does not configure - so writing it would be a comforting
     * lie. Instead, NO READ PATH IN THIS FEATURE LOADS THE ENTITY: list,
     * metadata and delete are all projection or bulk queries that cannot select
     * this column. See AiDocumentRepository.
     */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false)
    private byte[] content;

    /** length = 32 matches varchar(32); the default for an enum would be 255. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AiDocumentStatus status = AiDocumentStatus.PENDING;

    /**
     * Why processing failed, set only in the FAILED state by AI-2.
     *
     * A fixed, developer-written reason. It must NEVER carry document content,
     * an extracted fragment, or a provider's echo of either - this field is
     * returned to the client and written to logs.
     */
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AiDocument() {
    }

    /*
     * Both columns carry DEFAULT now(), but Hibernate always includes a mapped
     * column in its INSERT, so a null would hit the NOT NULL constraint rather
     * than falling back to the default. Same shape as BaseEntity, with Instant.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public AiDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(AiDocumentStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * REDACTED, AND FOR A BIGGER REASON THAN USUAL.
     *
     * The generated toString() of a class is not the hazard a record's is - but
     * `content` is a byte[], and any implementation that included it would emit
     * either a useless array identity or, if someone "improved" it with
     * Arrays.toString, a decimal dump of a user's legal document into the logs.
     * Naming the field and its length is everything a log line legitimately
     * needs.
     */
    @Override
    public String toString() {
        return "AiDocument{id=" + id
                + ", filename=" + filename
                + ", contentType=" + contentType
                + ", sizeBytes=" + sizeBytes
                + ", status=" + status
                + ", content=<" + (content == null ? 0 : content.length) + " bytes, not shown>}";
    }
}
