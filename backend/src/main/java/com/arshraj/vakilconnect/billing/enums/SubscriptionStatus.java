package com.arshraj.vakilconnect.billing.enums;

/**
 * Lifecycle of one {@code lawyer_subscriptions} row.
 *
 * PENDING -> ACTIVE happens only after the Razorpay payment signature is
 * verified server-side; a failed verification leaves the row PENDING so the
 * lawyer can retry rather than forcing a brand new order. EXPIRED and
 * CANCELLED are not yet set by any automated job - they exist so the schema
 * does not need another migration the day a renewal-expiry sweep is added.
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
