package com.arshraj.vakilconnect.reference.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import com.arshraj.vakilconnect.reference.enums.AliasSource;
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
 * An alternative name for a city.
 *
 * Not a nicety. India renamed many cities and both forms remain in daily use -
 * Bombay/Mumbai, Calcutta/Kolkata, Bangalore/Bengaluru, Gurgaon/Gurugram. A
 * dropdown without aliases is a WORSE experience than free text, because a
 * client typing "Bangalore" finds nothing and concludes the platform is empty.
 *
 * It has a second job: it is the mapping table for reconciling the existing
 * free-text `lawyers.city` values in a later phase, which is what makes that
 * backfill tractable rather than entirely manual.
 *
 * Uniqueness is (city, alias) rather than alias alone: one historical name can
 * legitimately resolve to several cities, and search disambiguates by showing
 * the state alongside each result.
 */
@Entity
@Table(
        name = "city_aliases",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_city_aliases_city_alias",
                columnNames = {"city_id", "alias_normalized"})
)
public class CityAlias extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, length = 120)
    private String alias;

    @Column(name = "alias_normalized", nullable = false, length = 120)
    private String aliasNormalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AliasSource source;

    public CityAlias() {
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getAliasNormalized() {
        return aliasNormalized;
    }

    public void setAliasNormalized(String aliasNormalized) {
        this.aliasNormalized = aliasNormalized;
    }

    public AliasSource getSource() {
        return source;
    }

    public void setSource(AliasSource source) {
        this.source = source;
    }
}
