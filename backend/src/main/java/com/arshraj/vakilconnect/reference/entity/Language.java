package com.arshraj.vakilconnect.reference.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A language a user speaks or prefers.
 *
 * Flat, not hierarchical - languages have no parent. They share the reference
 * MACHINERY (stable ids, curation, a cached public lookup) with geography, but
 * none of its structure, so they are deliberately not forced into a tree.
 *
 * `isoCode` is varchar(3), not varchar(2): six of India's scheduled languages -
 * Bodo, Dogri, Konkani, Maithili, Manipuri and Santali - have no ISO 639-1
 * two-letter code, only 639-2/3. A varchar(2) column would have made them
 * unrepresentable.
 *
 * `nativeName` is not decoration. A Marathi speaker scanning a dropdown should
 * see "मराठी", and on an India-first platform that is the difference between a
 * usable control and a list of English exonyms.
 */
@Entity
@Table(name = "languages")
public class Language extends BaseEntity {

    @Column(name = "iso_code", nullable = false, unique = true, length = 3)
    private String isoCode;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "native_name", nullable = false, length = 60)
    private String nativeName;

    @Column(nullable = false)
    private boolean active = true;

    public Language() {
    }

    public String getIsoCode() {
        return isoCode;
    }

    public void setIsoCode(String isoCode) {
        this.isoCode = isoCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNativeName() {
        return nativeName;
    }

    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
