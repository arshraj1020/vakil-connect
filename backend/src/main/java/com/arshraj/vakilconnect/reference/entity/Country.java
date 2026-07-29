package com.arshraj.vakilconnect.reference.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A country.
 *
 * Only India is seeded. This table exists so the SHAPE supports expansion -
 * seeding 250 countries the platform cannot serve would put dropdown options in
 * front of users that lead to an unusable profile. Adding one later is a single
 * INSERT, because the `states.country_id` FK is already in place.
 *
 * `iso2` is the natural business key (ISO 3166-1 alpha-2). `phoneCode` is kept
 * here rather than hardcoded because the current phone validation
 * (`^\+?[0-9]{10,15}$`) is India-shaped, and retrofitting international parsing
 * across existing rows is far harder than leaving room for it now.
 */
@Entity
@Table(name = "countries")
public class Country extends BaseEntity {

    @Column(nullable = false, unique = true, length = 2)
    private String iso2;

    @Column(nullable = false, unique = true, length = 3)
    private String iso3;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "phone_code", nullable = false, length = 8)
    private String phoneCode;

    /** Reference rows are deactivated, never deleted - FKs point at them. */
    @Column(nullable = false)
    private boolean active = true;

    public Country() {
    }

    public String getIso2() {
        return iso2;
    }

    public void setIso2(String iso2) {
        this.iso2 = iso2;
    }

    public String getIso3() {
        return iso3;
    }

    public void setIso3(String iso3) {
        this.iso3 = iso3;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public void setPhoneCode(String phoneCode) {
        this.phoneCode = phoneCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
