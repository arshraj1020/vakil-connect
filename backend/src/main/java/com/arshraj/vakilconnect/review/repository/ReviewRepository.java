package com.arshraj.vakilconnect.review.repository;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Page<Review> findByLawyerOrderByCreatedAtDesc(Lawyer lawyer, Pageable pageable);
}
