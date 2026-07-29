package com.arshraj.vakilconnect.reference.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A city.
 *
 * Uniqueness is scoped to the STATE, not global: "Aurangabad" exists in both
 * Maharashtra and Bihar, and several other names repeat across India. A global
 * constraint would reject legitimate data.
 *
 * The constraint is on `name_normalized`, not `name` - that is what makes it
 * mean anything. Against the raw column, "Mumbai" and "mumbai " would both be
 * accepted, which is precisely the free-text problem this table replaces.
 */
@Entity
@Table(
        name = "cities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cities_state_name",
                columnNames = {"state_id", "name_normalized"})
)
public class City extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 120)
    private String nameNormalized;

    /**
     * Deactivated cities disappear from PICKERS but must remain matchable in
     * SEARCH - otherwise retiring a city silently hides every lawyer in it,
     * which is the discoverability bug this whole design exists to fix.
     */
    @Column(nullable = false)
    private boolean active = true;

    public City() {
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
