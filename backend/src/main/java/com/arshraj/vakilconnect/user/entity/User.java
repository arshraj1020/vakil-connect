package com.arshraj.vakilconnect.user.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.entity.Language;
import com.arshraj.vakilconnect.user.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /* ------------------------------------------ two independent state flags --
     *
     * `emailVerified` and `active` answer different questions and must never be
     * conflated:
     *
     *   emailVerified -> has the user proved control of this mailbox?
     *                    Set by the user, by following a verification link.
     *
     *   active        -> is this account permitted to exist at all?
     *                    Set by an admin, via deactivation.
     *
     * They are enforced in different places and produce different responses, so
     * the frontend can tell "click the link we sent you" apart from "an admin
     * disabled your account".
     *
     * NAMING. This field was called `enabled` until Phase 0. That name collided
     * with Spring Security's UserDetails.isEnabled(), which CustomUserDetailsService
     * populates from `active` - so `user.isEnabled()` and `userDetails.isEnabled()`
     * sat in the same request path (JwtAuthenticationFilter) meaning opposite
     * columns. The column name `is_email_verified` was always correct; only the
     * Java field was wrong, so the rename needs no migration.
     */

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * When this account's credentials last changed (V7).
     *
     * The anchor for JWT invalidation: a token carries the value that was
     * current when it was issued, and once this moves forward every token
     * minted before it stops being accepted. Nothing reads it yet - the claim
     * and the filter check land in a later phase - so today it is written once,
     * at construction, and never again.
     *
     * Instant, not LocalDateTime, mapping to `timestamptz`. This value is
     * compared against "now" to decide whether a security token is still valid,
     * and a zone-less wall clock silently shifts meaning across a DST boundary
     * or a host in another region. The rest of this row still uses
     * LocalDateTime via BaseEntity; see the V7 header for why the inconsistency
     * is deliberate.
     *
     * Initialised at construction rather than in @PrePersist: the column is NOT
     * NULL, Hibernate always includes it in the INSERT, and a null would
     * therefore hit the constraint rather than fall back to the database
     * DEFAULT. BaseEntity already owns the only @PrePersist on this hierarchy.
     */
    @Column(name = "credentials_changed_at", nullable = false)
    private Instant credentialsChangedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CLIENT;

    @Column(columnDefinition = "boolean default true")
    private boolean active = true;

    public User() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Normalises on write, so the column only ever holds trimmed lowercase.
     *
     * Locale.ROOT is required, not decorative: the no-arg toLowerCase() uses the
     * JVM default locale, and in a Turkish locale 'I' lowercases to the dotless
     * 'ı'. An address normalised on a Turkish-default host would then never match
     * one normalised anywhere else.
     *
     * AuthServiceImpl.normalizeEmail applies the identical rule before lookups.
     * The two MUST agree - a write rule and a read rule that disagree is exactly
     * the class of bug Phase 0 fixed in login().
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /** True once the user has followed a verification link. See the note above. */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    /**
     * Moving this forward invalidates every JWT issued before the new value.
     * Call it from any path that changes a credential, never for anything else.
     */
    public void setCredentialsChangedAt(Instant credentialsChangedAt) {
        this.credentialsChangedAt = credentialsChangedAt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /* ------------------------------------------------- reference data (V4) --
     * Added in Phase 2B. Both are optional for every role and nothing reads
     * them yet - no DTO exposes them, and registration does not set them.
     *
     * LAZY and nullable. A nullable LAZY @ManyToOne on the OWNING side is safe
     * to proxy, because Hibernate reads the FK value from this row and knows
     * whether there is anything to resolve.
     *
     * The practical caution is the same one that produced the Phase 1 defect:
     * `User` is mapped to a DTO in several places (toUserSummary,
     * toProfileResponse, getCurrentUser). None of them touch these fields
     * today. The moment one does, that method must be @Transactional or it will
     * throw LazyInitializationException outside a session.
     *
     * No cascade: cities and languages are shared reference rows.
     */

    /** Where the user is based. Optional; clients need it only for suggestions. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    /** Preferred language for communication. Optional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_language_id")
    private Language preferredLanguage;

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public Language getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(Language preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }
}
