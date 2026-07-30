package com.arshraj.vakilconnect.lawyer.repository;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LawyerRepository extends JpaRepository<Lawyer, UUID> {

    Optional<Lawyer> findByUser(User user);

    Optional<Lawyer> findByBarCouncilNumber(String barCouncilNumber);

    boolean existsByUser(User user);

    boolean existsByBarCouncilNumber(String barCouncilNumber);

    /**
     * The admin verification queue.
     *
     * The entity graph is Phase 2G. Every row is mapped by toSummaryResponse,
     * which now reads `primaryCity` for the city value; without the graph that
     * is one extra SELECT per lawyer on the page. `primaryCity` is a @ManyToOne,
     * so fetching it adds a join without multiplying rows - pagination is still
     * applied in SQL, unlike a collection fetch.
     */
    @EntityGraph(attributePaths = "primaryCity")
    Page<Lawyer> findByVerifiedFalse(Pageable pageable);

    long countByVerifiedTrue();

    long countByVerifiedFalse();

    /* ------------------------------------------ reconciliation (Phase 2F) --
     * Read-only counters backing the migration readiness report. They exist so
     * the state of the backfill is observable before anyone considers a read
     * cut-over.
     */

    long countByPrimaryCityIsNull();

    long countByPracticeCitiesIsEmpty();

    long countByLanguagesIsEmpty();

    /**
     * The distinct legacy city strings that failed to resolve.
     *
     * This is the actionable half of the report: each value is either a typo to
     * correct, a city to seed, or an alias to add. Distinct rather than per-row,
     * because one misspelling usually accounts for many lawyers.
     */
    @Query("""
            SELECT DISTINCT l.city FROM Lawyer l
            WHERE l.primaryCity IS NULL
              AND l.city IS NOT NULL
            ORDER BY l.city
            """)
    List<String> findUnresolvedCityNames();

    /* ------------------------------------------- reference data (Phase 2B) --
     *
     * Fetch-join loaders for the associations added in V4. Nothing in the
     * application calls them yet - they exist because the alternative is for
     * callers to touch a LAZY collection and hope a transaction is open, which
     * is precisely the failure that produced the Phase 1 defect.
     *
     * Deliberately one query per collection. `practiceCities` and `languages`
     * are both Sets, so Hibernate would permit fetching them together, but the
     * result would be a cartesian product of the two - N x M rows for a single
     * lawyer. Two round trips beats one bad join.
     */

    /** One lawyer with `practiceCities` and each city's state resolved. */
    @Query("""
            SELECT l FROM Lawyer l
            LEFT JOIN FETCH l.practiceCities c
            LEFT JOIN FETCH c.state
            WHERE l.id = :id
            """)
    Optional<Lawyer> findByIdWithPracticeCities(@Param("id") UUID id);

    /** One lawyer with `languages` resolved. */
    @Query("""
            SELECT l FROM Lawyer l
            LEFT JOIN FETCH l.languages
            WHERE l.id = :id
            """)
    Optional<Lawyer> findByIdWithLanguages(@Param("id") UUID id);

    /**
     * One lawyer with `primaryCity` and its state resolved.
     *
     * LEFT JOIN, not JOIN: `primary_city_id` is nullable until the backfill
     * phase populates it, and an inner join would silently return no row for
     * every lawyer that has not been migrated yet.
     */
    @Query("""
            SELECT l FROM Lawyer l
            LEFT JOIN FETCH l.primaryCity pc
            LEFT JOIN FETCH pc.state
            WHERE l.id = :id
            """)
    Optional<Lawyer> findByIdWithPrimaryCity(@Param("id") UUID id);

    /**
     * THE CITY PREDICATE IS THE PHASE 2G CUT-OVER (everything else is unchanged).
     *
     * Two branches, and a lawyer is evaluated by exactly one of them:
     *
     *   reference   the lawyer HAS practice cities -> match against
     *               `pc.nameNormalized`, the curated axis
     *   legacy      the lawyer has NO practice cities -> match against
     *               `lawyers.city`, exactly as before
     *
     * =========================================================================
     * WHY THE GATE IS `practiceCities IS EMPTY` AND NOT `primaryCity IS NULL`
     *
     * These two conditions agree on every correctly-migrated row, so the choice
     * looks arbitrary until you ask what happens when they DISAGREE. They are
     * not interchangeable, and picking the wrong one silently deletes lawyers
     * from search results.
     *
     * -- Why primaryCity alone is insufficient ------------------------------
     *
     * `primaryCity` answers "which single city do we DISPLAY for this lawyer".
     * It says nothing about which cities this lawyer can be FOUND by, and it is
     * not the column this predicate reads. Using it as the gate means deciding
     * whether the reference branch can answer by inspecting a different field
     * from the one the reference branch actually queries.
     *
     * Concretely, for a row with `primary_city_id` set but no rows in
     * `lawyer_practice_cities`:
     *
     *   gate on primaryCity IS NULL  -> not null, so the reference branch is
     *                                   selected -> EXISTS runs against an
     *                                   empty collection -> false -> and the
     *                                   legacy branch is closed because the
     *                                   gate already chose. The lawyer matches
     *                                   NEITHER branch and is unreachable by
     *                                   any city search, for every city,
     *                                   permanently and silently.
     *
     *   gate on practiceCities EMPTY -> empty, so the legacy branch is selected
     *                                   -> `lawyers.city` still answers, which
     *                                   is precisely the pre-cut-over result.
     *
     * The mirror case is just as real once a multi-city UI exists: practice
     * cities present but no primary chosen. Gating on the primary would push
     * that lawyer onto the legacy string and ignore reference data that is
     * sitting right there.
     *
     * -- Why practiceCities is the authoritative signal ---------------------
     *
     * The gate and the predicate must read the same collection. This query asks
     * "is this lawyer associated with city X", `lawyer_practice_cities` is the
     * table that answers it, and so its emptiness is the only honest test of
     * whether the reference model has anything to say about this row. Any other
     * gate is a proxy that can disagree with the data being queried.
     *
     * Option C makes practiceCities a superset of the primary by design - the
     * service maintains `primary IN practice` (LawyerServiceImpl#applyCityReference)
     * and V6 asserted it for backfilled rows - so gating on the superset can
     * never exclude a row the narrower gate would have included.
     *
     * -- What regression this avoids ----------------------------------------
     *
     * A verified lawyer disappearing from every city search while their profile
     * page still renders correctly. That is the worst shape of bug this codebase
     * has already produced once (the Phase 1 "verified but 500" defect): the
     * write succeeded, the data looks right on inspection, and the damage is
     * only visible in a query nobody thought to re-run. Here it would also be
     * invisible to the Phase 2F reconciliation report, which counts unmigrated
     * rows via `primary_city_id IS NULL` - a row with a primary but no practice
     * rows is reported as fully migrated.
     *
     * -- This branch INTENTIONALLY TOLERATES partially migrated rows ---------
     *
     * The two gates differ only on rows that violate the Option C invariant,
     * i.e. rows that should not exist. This predicate deliberately does not
     * treat those as an error, hide them, or assume they are absent. It answers
     * them from the legacy column and keeps the lawyer findable.
     *
     * That tolerance is the point, and it is chosen with the trade-off in view:
     * the cost is that a corrupt row is served slightly stale data instead of
     * failing loudly, which delays discovery. The alternative cost is a lawyer
     * vanishing from the product. Degrading to the previous correct answer is
     * the failure direction that loses nobody, and it is the same principle as
     * the 2F backfill (under-link rather than mis-link) and the 2E dual-write
     * (clear a contradicted link rather than preserve it).
     *
     * Loud detection belongs in reconciliation, not in the read path. If a
     * future phase wants these rows surfaced, add a counter to
     * ReferenceReconciliationService - do NOT tighten this gate.
     * =========================================================================
     *
     * `:cityNormalized` is the CANONICAL normalised name the caller resolved the
     * search term to - not the raw term. That resolution is alias-aware, and it
     * has to be: after the backfill, a lawyer who typed "Bombay" is linked to
     * Mumbai and their normalised practice name is "mumbai". Matching the raw
     * term would drop them from a "Bombay" search that used to find them. When
     * the term names no curated city the caller passes null, `pc.nameNormalized
     * = NULL` is never true, and only the legacy branch can match - which is the
     * pre-cut-over behaviour for exactly the rows that were never linked.
     *
     * The correlated EXISTS is written in portable JPQL (re-rooted on Lawyer and
     * joined back by id) rather than as a subquery over `l.practiceCities`, so
     * it does not depend on an HQL extension.
     *
     * ---------------------------------------------------------------------
     * The nullable text parameters are explicitly CAST to String.
     *
     * Without the cast, a null keyword/specialization/city is bound as an
     * untyped JDBC null; the PostgreSQL driver then sends it with the bytea
     * OID, and resolving LOWER(bytea) fails at parse/plan time with
     * "function lower(bytea) does not exist" — even though the ":param IS NULL"
     * branch would short-circuit at runtime, because PostgreSQL must resolve
     * every function's argument types before executing anything.
     *
     * CAST(:param AS String) forces a varchar bind, so LOWER(text) resolves.
     * CAST(NULL AS varchar) is still NULL, so the IS NULL semantics are unchanged.
     * `:cityNormalized` carries the same cast for the same reason: it is
     * routinely null, and it is compared against a varchar column.
     *
     * The specialization predicate needs no cut-over. `lawyer_specializations`
     * has been a join table onto an entity since V1 - there has never been a
     * denormalised specialization string to migrate away from, so this branch
     * already reads the reference model.
     */
    @EntityGraph(attributePaths = "primaryCity")
    @Query("""
            SELECT DISTINCT l FROM Lawyer l LEFT JOIN l.specializations s
            WHERE l.verified = true
            AND (:keyword IS NULL
                 OR LOWER(l.user.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))
                 OR LOWER(l.bio) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%')))
            AND (:specialization IS NULL OR LOWER(s.name) = LOWER(CAST(:specialization AS String)))
            AND (:city IS NULL
                 OR EXISTS (SELECT 1 FROM Lawyer lc JOIN lc.practiceCities pc
                            WHERE lc.id = l.id
                              AND pc.nameNormalized = CAST(:cityNormalized AS String))
                 OR (l.practiceCities IS EMPTY
                     AND LOWER(l.city) = LOWER(CAST(:city AS String))))
            AND (:minFee IS NULL OR l.consultationFee >= :minFee)
            AND (:maxFee IS NULL OR l.consultationFee <= :maxFee)
            AND (:minExperience IS NULL OR l.experienceYears >= :minExperience)
            AND (:minRating IS NULL OR l.rating >= :minRating)
            """)
    Page<Lawyer> search(
            @Param("keyword") String keyword,
            @Param("specialization") String specialization,
            @Param("city") String city,
            @Param("cityNormalized") String cityNormalized,
            @Param("minFee") BigDecimal minFee,
            @Param("maxFee") BigDecimal maxFee,
            @Param("minExperience") Integer minExperience,
            @Param("minRating") Double minRating,
            Pageable pageable
    );
}