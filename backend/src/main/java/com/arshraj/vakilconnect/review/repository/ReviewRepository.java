package com.arshraj.vakilconnect.review.repository;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.review.entity.Review;
import com.arshraj.vakilconnect.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Page<Review> findByLawyerOrderByCreatedAtDesc(Lawyer lawyer, Pageable pageable);

    /**
     * Reviews written by this client. Additive (Phase 4).
     *
     * One half of the "no dependent rows" test that gates unverified-account
     * takeover and purging - AppointmentRepository.countByClient is the other.
     * A single review means a real person used the account, so the address is
     * theirs and must not be reclaimed or deleted.
     */
    long countByClient(User client);
}
