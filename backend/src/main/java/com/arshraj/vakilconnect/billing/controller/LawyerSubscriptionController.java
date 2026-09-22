package com.arshraj.vakilconnect.billing.controller;

import com.arshraj.vakilconnect.billing.dto.CouponValidationResponse;
import com.arshraj.vakilconnect.billing.dto.CreateSubscriptionOrderRequest;
import com.arshraj.vakilconnect.billing.dto.SubscriptionOrderResponse;
import com.arshraj.vakilconnect.billing.dto.SubscriptionStatusResponse;
import com.arshraj.vakilconnect.billing.dto.ValidateCouponRequest;
import com.arshraj.vakilconnect.billing.dto.VerifySubscriptionPaymentRequest;
import com.arshraj.vakilconnect.billing.enums.SubscriptionPlan;
import com.arshraj.vakilconnect.billing.service.LawyerSubscriptionService;
import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lawyer-facing subscription endpoints. Covered by {@code /api/lawyer/**} ->
 * {@code hasRole("LAWYER")} in SecurityConfig, so every method here can trust
 * {@code authentication.getName()} to be the email of a logged-in lawyer.
 *
 * The Razorpay webhook lives in {@link RazorpayWebhookController}, not here -
 * it is deliberately NOT under {@code /api/lawyer/**} because it is called by
 * Razorpay's servers with no JWT at all, and needs its own permit-all entry
 * in SecurityConfig.
 */
@RestController
@RequestMapping("/api/lawyer/subscription")
public class LawyerSubscriptionController {

    private final LawyerSubscriptionService subscriptionService;

    public LawyerSubscriptionController(LawyerSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public SubscriptionStatusResponse getStatus(Authentication authentication) {
        return subscriptionService.getCurrentStatus(authentication.getName());
    }

    @PostMapping("/orders")
    public SubscriptionOrderResponse createOrder(@Valid @RequestBody CreateSubscriptionOrderRequest request,
                                                  Authentication authentication) {
        SubscriptionPlan plan = parsePlan(request.getPlan());
        return subscriptionService.createOrder(authentication.getName(), plan, request.getCouponCode());
    }

    @PostMapping("/verify")
    public SubscriptionStatusResponse verify(@Valid @RequestBody VerifySubscriptionPaymentRequest request,
                                              Authentication authentication) {
        return subscriptionService.verifyPayment(
                authentication.getName(), request.getOrderId(), request.getPaymentId(), request.getSignature());
    }

    /**
     * Read-only check used by the "Apply" button on the subscription page -
     * lets the frontend show the discounted price before the lawyer commits
     * to Checkout, without creating an order yet.
     */
    @PostMapping("/coupons/validate")
    public CouponValidationResponse validateCoupon(@Valid @RequestBody ValidateCouponRequest request) {
        return subscriptionService.validateCoupon(request.getCode());
    }

    private SubscriptionPlan parsePlan(String plan) {
        try {
            return SubscriptionPlan.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown plan: " + plan);
        }
    }
}
