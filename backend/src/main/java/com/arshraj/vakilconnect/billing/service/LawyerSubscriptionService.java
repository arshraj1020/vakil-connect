package com.arshraj.vakilconnect.billing.service;

import com.arshraj.vakilconnect.billing.dto.SubscriptionOrderResponse;
import com.arshraj.vakilconnect.billing.dto.SubscriptionStatusResponse;
import com.arshraj.vakilconnect.billing.enums.SubscriptionPlan;

public interface LawyerSubscriptionService {

    /** @param userEmail the authenticated lawyer's email, as read from Authentication#getName(). */
    SubscriptionStatusResponse getCurrentStatus(String userEmail);

    SubscriptionOrderResponse createOrder(String userEmail, SubscriptionPlan plan);

    SubscriptionStatusResponse verifyPayment(String userEmail, String orderId,
                                              String paymentId, String signature);

    /**
     * Razorpay's own notification of a payment event. Best-effort: never
     * throws, and always intended to be answered 200 by the controller so
     * Razorpay does not retry-storm on a shape this could not parse.
     */
    void handleWebhook(String rawBody, String signatureHeader);
}
