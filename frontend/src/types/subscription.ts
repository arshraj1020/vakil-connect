import type { IsoDateTime } from "./common";

/**
 * Razorpay-backed lawyer subscription contracts (Rs 500/month, Rs 5500/year).
 *
 * Mirrors the backend's billing package exactly:
 * `SubscriptionOrderResponse` / `SubscriptionStatusResponse` in
 * `com.arshraj.vakilconnect.billing.dto`.
 */

export type SubscriptionPlan = "MONTHLY" | "YEARLY";

export type SubscriptionStatus = "PENDING" | "ACTIVE" | "EXPIRED" | "CANCELLED";

/**
 * The authenticated lawyer's current subscription state.
 *
 * `plan`/`status`/`startsAt`/`expiresAt` are all `null` when the lawyer has
 * never subscribed - there is no row yet, not an error.
 *
 * `active` is server-computed (status === "ACTIVE" && expiresAt in the
 * future), never re-derived on the client - see
 * `SubscriptionStatusResponse`'s doc comment on the backend for why a stored
 * ACTIVE status can still be expired.
 */
export interface SubscriptionStatusResponse {
  plan: SubscriptionPlan | null;
  status: SubscriptionStatus | null;
  active: boolean;
  startsAt: IsoDateTime | null;
  expiresAt: IsoDateTime | null;
}

/**
 * Returned by POST /api/lawyer/subscription/orders.
 *
 * `orderId`/`keyId` are null and `requiresPayment` is false only for a
 * 100%-off coupon - the subscription is already active by the time this
 * response comes back, and the caller must not open Razorpay Checkout.
 */
export interface SubscriptionOrderResponse {
  orderId: string | null;
  /** Razorpay's PUBLIC key id - safe to hand to `new window.Razorpay({...})`. */
  keyId: string | null;
  amountPaise: number;
  originalAmountPaise: number;
  discountPercent: number;
  currency: string;
  plan: SubscriptionPlan;
  requiresPayment: boolean;
}

/** Body for POST /api/lawyer/subscription/verify - Razorpay Checkout's `handler` payload. */
export interface VerifySubscriptionPaymentRequest {
  orderId: string;
  paymentId: string;
  signature: string;
}

/** Returned by POST /api/lawyer/subscription/coupons/validate. */
export interface CouponValidationResponse {
  code: string;
  discountPercent: number;
}
