package com.arshraj.vakilconnect.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/lawyer/subscription/coupons/validate. */
public class ValidateCouponRequest {

    @NotBlank
    private String code;

    public ValidateCouponRequest() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
