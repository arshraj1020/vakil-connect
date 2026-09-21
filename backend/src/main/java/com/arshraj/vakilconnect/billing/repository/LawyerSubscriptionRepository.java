package com.arshraj.vakilconnect.billing.repository;

import com.arshraj.vakilconnect.billing.entity.LawyerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LawyerSubscriptionRepository extends JpaRepository<LawyerSubscription, UUID> {

    /**
     * The lawyer's current subscription - the most recent row by created_at,
     * per the "each renewal is a new row" convention documented in V11.
     */
    Optional<LawyerSubscription> findTopByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    Optional<LawyerSubscription> findByRazorpayOrderId(String razorpayOrderId);
}
