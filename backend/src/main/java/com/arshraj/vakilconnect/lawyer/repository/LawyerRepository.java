package com.arshraj.vakilconnect.lawyer.repository;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface LawyerRepository extends JpaRepository<Lawyer, UUID> {

    Optional<Lawyer> findByUser(User user);

    Optional<Lawyer> findByBarCouncilNumber(String barCouncilNumber);

    boolean existsByUser(User user);

    boolean existsByBarCouncilNumber(String barCouncilNumber);

    Page<Lawyer> findByVerifiedFalse(Pageable pageable);

    long countByVerifiedTrue();

    long countByVerifiedFalse();

    /*
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
     */
    @Query("""
            SELECT DISTINCT l FROM Lawyer l LEFT JOIN l.specializations s
            WHERE l.verified = true
            AND (:keyword IS NULL
                 OR LOWER(l.user.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))
                 OR LOWER(l.bio) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%')))
            AND (:specialization IS NULL OR LOWER(s.name) = LOWER(CAST(:specialization AS String)))
            AND (:city IS NULL OR LOWER(l.city) = LOWER(CAST(:city AS String)))
            AND (:minFee IS NULL OR l.consultationFee >= :minFee)
            AND (:maxFee IS NULL OR l.consultationFee <= :maxFee)
            AND (:minExperience IS NULL OR l.experienceYears >= :minExperience)
            AND (:minRating IS NULL OR l.rating >= :minRating)
            """)
    Page<Lawyer> search(
            @Param("keyword") String keyword,
            @Param("specialization") String specialization,
            @Param("city") String city,
            @Param("minFee") BigDecimal minFee,
            @Param("maxFee") BigDecimal maxFee,
            @Param("minExperience") Integer minExperience,
            @Param("minRating") Double minRating,
            Pageable pageable
    );
}