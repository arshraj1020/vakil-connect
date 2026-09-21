package com.arshraj.vakilconnect.billing.enums;

/**
 * The two subscription plans a lawyer can pay for to join the platform.
 *
 * Amounts are in PAISE (the smallest INR unit), matching what Razorpay's
 * order API expects for `amount`. Hard-coded here rather than configuration:
 * these are product prices, not deployment-environment knobs, and putting
 * them in application.yaml would let ₹500/month silently become something
 * else on one environment without anyone reviewing a price change.
 */
public enum SubscriptionPlan {

    MONTHLY(49_900),
    YEARLY(549_900);

    private final int amountPaise;

    SubscriptionPlan(int amountPaise) {
        this.amountPaise = amountPaise;
    }

    public int getAmountPaise() {
        return amountPaise;
    }
}
