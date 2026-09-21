package com.arshraj.vakilconnect.billing.dto;

/**
 * Returned by POST /api/lawyer/subscription/orders.
 *
 * `keyId` is the Razorpay PUBLIC key - safe to send to the browser, which
 * needs it to construct `new Razorpay({...})` for Checkout.
 */
public class SubscriptionOrderResponse {

    private String orderId;
    private String keyId;
    private int amountPaise;
    private String currency;
    private String plan;

    public SubscriptionOrderResponse() {
    }

    public SubscriptionOrderResponse(String orderId, String keyId, int amountPaise,
                                      String currency, String plan) {
        this.orderId = orderId;
        this.keyId = keyId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.plan = plan;
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
}
