package com.arshraj.vakilconnect.billing.repository;

import com.arshraj.vakilconnect.billing.entity.LawyerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LawyerSubscriptionRepository extends JpaRepository<LawyerSubscription, UUID> {

    /**
     * The lawyer's current subscription - the most recent row by created_at,
     * per the "each renewal is a new row" convention documented in V11.
     */
    Optional<LawyerSubscription> findTopByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    /**
     * Every subscription row for this lawyer, newest first. A lawyer can end
     * up with more than one PENDING row (a re-click of Subscribe before the
     * first order's payment settles, an abandoned Checkout, a stale attempt
     * from testing) - reconciliation has to sweep all of them, not just the
     * most recent, or a payment tied to an older row is silently ignored.
     */
    List<LawyerSubscription> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    Optional<LawyerSubscription> findByRazorpayOrderId(String razorpayOrderId);
}
