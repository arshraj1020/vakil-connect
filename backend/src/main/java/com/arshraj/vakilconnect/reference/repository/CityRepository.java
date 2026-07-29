package com.arshraj.vakilconnect.reference.repository;

import com.arshraj.vakilconnect.reference.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CityRepository extends JpaRepository<City, UUID> {

    /** Backs the state -> city dependent dropdown. */
    List<City> findByStateIdAndActiveTrueOrderByNameAsc(UUID stateId);

    /** The exact-match path: uniqueness is scoped to the state. */
    Optional<City> findByStateIdAndNameNormalized(UUID stateId, String nameNormalized);

    long countByStateId(UUID stateId);

    /**
     * Typeahead over canonical names.
     *
     * `LIKE %term%` rather than a prefix match, because users type fragments -
     * and the GIN trigram index on `name_normalized` serves an infix LIKE,
     * which a btree cannot. The caller passes an already-normalised term.
     *
     * Fetches the state and country eagerly: every result is rendered as
     * "Pune, Maharashtra" for disambiguation, and without the join this would
     * be N+1 across the result set - the same failure family as the Phase 1
     * lazy-loading defect, at list scale.
     */
    @Query("""
            SELECT c FROM City c
            JOIN FETCH c.state s
            JOIN FETCH s.country
            WHERE c.active = true
              AND c.nameNormalized LIKE CONCAT('%', :term, '%')
            ORDER BY c.name ASC
            """)
    List<City> searchByNormalizedTerm(@Param("term") String term);

    /** Loads one city with its parents resolved, for callers outside a transaction. */
    @Query("""
            SELECT c FROM City c
            JOIN FETCH c.state s
            JOIN FETCH s.country
            WHERE c.id = :id
            """)
    Optional<City> findByIdWithHierarchy(@Param("id") UUID id);
}
