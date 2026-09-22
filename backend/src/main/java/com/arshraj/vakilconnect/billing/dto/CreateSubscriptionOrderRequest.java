package com.arshraj.vakilconnect.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/lawyer/subscription/orders. */
public class CreateSubscriptionOrderRequest {

    @NotBlank
    private String plan;

    /** Optional. A coupon code such as ARSHCARE/ARSHFRIEND/ARSHFAMILY - blank/absent means no discount. */
    private String couponCode;

    public CreateSubscriptionOrderRequest() {
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }
}
