package com.arshraj.vakilconnect.reference.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import com.arshraj.vakilconnect.reference.enums.StateType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A state or union territory.
 *
 * `code` is unique WITHIN a country, not globally - two countries may both use
 * "CA". The composite constraint says so explicitly.
 *
 * The country association is LAZY. Every association in this package is, and
 * every service method that maps one into a DTO must be @Transactional - that
 * is exactly the defect fixed in Phase 1, and adding more lazy associations
 * makes it easier to reintroduce, not harder.
 */
@Entity
@Table(
        name = "states",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_states_country_code",
                columnNames = {"country_id", "code"})
)
public class State extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Canonical lookup key. See TextNormalizer. */
    @Column(name = "name_normalized", nullable = false, length = 100)
    private String nameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StateType type;

    @Column(nullable = false)
    private boolean active = true;

    public State() {
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameNormalized() {
        return nameNormalized;
    }

    public void setNameNormalized(String nameNormalized) {
        this.nameNormalized = nameNormalized;
    }

    public StateType getType() {
        return type;
    }

    public void setType(StateType type) {
        this.type = type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
