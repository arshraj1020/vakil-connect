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

    @Column(name = "is_email_verified", nullable = false)
    private boolean enabled = false;

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

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase();
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
