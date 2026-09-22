package com.arshraj.vakilconnect.billing.dto;

/**
 * Returned by POST /api/lawyer/subscription/orders.
 *
 * `keyId` is the Razorpay PUBLIC key - safe to send to the browser, which
 * needs it to construct `new Razorpay({...})` for Checkout.
 *
 * `requiresPayment` is false only for a 100%-off coupon: there, the
 * subscription is activated immediately server-side (Razorpay does not
 * support a zero-amount order), `orderId`/`keyId` are null, and the frontend
 * must not open Checkout at all - it should just treat the subscription as
 * active.
 */
public class SubscriptionOrderResponse {

    private String orderId;
    private String keyId;
    private int amountPaise;
    private int originalAmountPaise;
    private int discountPercent;
    private String currency;
    private String plan;
    private boolean requiresPayment;

    public SubscriptionOrderResponse() {
    }

    public SubscriptionOrderResponse(String orderId, String keyId, int amountPaise, int originalAmountPaise,
                                      int discountPercent, String currency, String plan, boolean requiresPayment) {
        this.orderId = orderId;
        this.keyId = keyId;
        this.amountPaise = amountPaise;
        this.originalAmountPaise = originalAmountPaise;
        this.discountPercent = discountPercent;
        this.currency = currency;
        this.plan = plan;
        this.requiresPayment = requiresPayment;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public int getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(int amountPaise) {
        this.amountPaise = amountPaise;
    }

    public int getOriginalAmountPaise() {
        return originalAmountPaise;
    }

    public void setOriginalAmountPaise(int originalAmountPaise) {
        this.originalAmountPaise = originalAmountPaise;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public boolean isRequiresPayment() {
        return requiresPayment;
    }

    public void setRequiresPayment(boolean requiresPayment) {
        this.requiresPayment = requiresPayment;
    }
}
