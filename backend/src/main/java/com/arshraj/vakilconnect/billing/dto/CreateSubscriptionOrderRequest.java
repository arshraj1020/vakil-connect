package com.arshraj.vakilconnect.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/lawyer/subscription/orders. */
public class CreateSubscriptionOrderRequest {

    @NotBlank
    private String plan;

    public CreateSubscriptionOrderRequest() {
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }
}
