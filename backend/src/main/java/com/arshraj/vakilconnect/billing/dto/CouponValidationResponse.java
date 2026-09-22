package com.arshraj.vakilconnect.billing.dto;

/**
 * Returned by POST /api/lawyer/subscription/coupons/validate.
 *
 * Lets the frontend show the discounted price BEFORE the lawyer commits to
 * Checkout - a lawyer typing a code and clicking "Apply" gets an immediate
 * yes/no and a price preview, rather than only finding out a code was wrong
 * after opening Razorpay's modal.
 */
public class CouponValidationResponse {

    private String code;
    private int discountPercent;

    public CouponValidationResponse() {
    }

    public CouponValidationResponse(String code, int discountPercent) {
        this.code = code;
        this.discountPercent = discountPercent;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }
}
