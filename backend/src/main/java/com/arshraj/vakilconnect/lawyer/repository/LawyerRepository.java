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

    @Query("""
            SELECT DISTINCT l FROM Lawyer l LEFT JOIN l.specializations s
            WHERE l.verified = true
            AND (:keyword IS NULL
                 OR LOWER(l.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(l.bio) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:specialization IS NULL OR LOWER(s.name) = LOWER(:specialization))
            AND (:city IS NULL OR LOWER(l.city) = LOWER(:city))
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