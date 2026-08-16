package com.arshraj.vakilconnect.identity.entity;

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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring secret sent to a user's mailbox.
 *
 * MAPPED TO A TABLE THAT ALREADY EXISTS IN PRODUCTION (V7). Every column below
 * was read out of the applied migration; nothing here may drift from it,
 * because `ddl-auto: validate` turns a mismatch into a refusal to start rather
 * than a runtime error.
 *
 * DELIBERATELY DOES NOT EXTEND BaseEntity. Two independent reasons:
 *
 *   1. BaseEntity declares `updated_at NOT NULL`, and `email_tokens` HAS NO
 *      SUCH COLUMN. Inheriting it would fail validation at boot - a production
 *      outage, not a test failure.
 *   2. BaseEntity's timestamps are LocalDateTime (zone-less, JVM wall clock).
 *      Every timestamp here is compared against "now" to decide whether a
 *      security token is still valid, so they are Instant/timestamptz. A
 *      zone-less expiry silently shifts meaning across a DST boundary.
 *
 * A token is immutable once issued except for reaching a terminal state, and
 * `usedAt` / `invalidatedAt` record that with more information than a generic
 * `updated_at` would - which is why the absence of that column is a design
 * decision rather than an omission.
 *
 * THE RAW TOKEN IS NOT A FIELD ON THIS CLASS. Only its HMAC is stored. Once
 * issue() returns, the raw value exists solely in the user's inbox.
 *
 * The three audit columns present in the table (requested_ip,
 * requested_user_agent, consumed_ip) are intentionally NOT mapped in this
 * phase: nothing can populate them until a caller holds an HttpServletRequest,
 * which arrives with the verification flow. Hibernate `validate` ignores
 * columns the entity does not map, so leaving them out is safe and keeps the
 * unused surface at zero.
 */
@Entity
@Table(name = "email_tokens")
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /*
     * LAZY: consuming a token needs the owning user, but invalidating or
     * purging one does not, and `open-in-view: false` means every access must
     * be inside a transaction anyway. Repository methods that DO need the user
     * JOIN FETCH it explicitly rather than relying on a proxy surviving.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** length = 32 matches varchar(32); the default for an enum would be 255. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private EmailTokenType type;

    /** Hex HMAC-SHA256 - exactly 64 characters, matching varchar(64). */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the user consumed the token. Mutually exclusive with invalidatedAt. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Set when we superseded or revoked the token. Mutually exclusive with usedAt. */
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    /*
     * The column carries DEFAULT now(), but Hibernate always includes a mapped
     * column in its INSERT, so a null would hit the NOT NULL constraint instead
     * of falling back to the default. Initialised at construction for the same
     * reason User.credentialsChangedAt is.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public EmailToken() {
    }

    /** True when the token is neither consumed, superseded, nor past its expiry. */
    public boolean isLive(Instant now) {
        return usedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
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

    public EmailTokenType getType() {
        return type;
    }

    public void setType(EmailTokenType type) {
        this.type = type;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Never includes the hash. A token hash in a log line is not as bad as a raw
     * token, but it is still the lookup key for a live credential.
     */
    @Override
    public String toString() {
        return "EmailToken{id=" + id + ", type=" + type + ", expiresAt=" + expiresAt + "}";
    }
}
