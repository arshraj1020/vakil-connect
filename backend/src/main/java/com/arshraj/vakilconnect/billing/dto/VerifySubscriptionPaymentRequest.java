package com.arshraj.vakilconnect.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/lawyer/subscription/verify - the three values Razorpay Checkout's `handler` callback receives. */
public class VerifySubscriptionPaymentRequest {

    @NotBlank
    private String orderId;

    @NotBlank
    private String paymentId;

    @NotBlank
    private String signature;

    public VerifySubscriptionPaymentRequest() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
