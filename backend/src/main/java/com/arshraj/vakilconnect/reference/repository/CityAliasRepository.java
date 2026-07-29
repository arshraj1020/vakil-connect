package com.arshraj.vakilconnect.reference.repository;

import com.arshraj.vakilconnect.reference.entity.CityAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CityAliasRepository extends JpaRepository<CityAlias, UUID> {

    /**
     * Resolves a historical or colloquial name to its cities.
     *
     * Returns a LIST, not an Optional: one alias can legitimately resolve to
     * several cities in different states, and the caller disambiguates by
     * showing the state. Collapsing that to a single result would silently pick
     * a winner.
     *
     * The hierarchy is fetched because every caller renders the state.
     */
    @Query("""
            SELECT a FROM CityAlias a
            JOIN FETCH a.city c
            JOIN FETCH c.state s
            JOIN FETCH s.country
            WHERE a.aliasNormalized = :aliasNormalized
            """)
    List<CityAlias> findByAliasNormalizedWithCity(@Param("aliasNormalized") String aliasNormalized);

    List<CityAlias> findByCityId(UUID cityId);

    boolean existsByCityIdAndAliasNormalized(UUID cityId, String aliasNormalized);
}
