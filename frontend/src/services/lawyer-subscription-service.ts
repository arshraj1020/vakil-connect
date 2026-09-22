import api from "@/lib/axios";
import type {
  CouponValidationResponse,
  SubscriptionOrderResponse,
  SubscriptionPlan,
  SubscriptionStatusResponse,
  VerifySubscriptionPaymentRequest,
} from "@/types";

/**
 * Lawyer subscription billing (Razorpay).
 *
 * All three endpoints are under `/api/lawyer/subscription`, covered by
 * `hasRole("LAWYER")` in SecurityConfig - there is no client-side role check
 * here because the route section already guards on it.
 */

const ENDPOINTS = {
  status: "/api/lawyer/subscription",
  orders: "/api/lawyer/subscription/orders",
  verify: "/api/lawyer/subscription/verify",
  validateCoupon: "/api/lawyer/subscription/coupons/validate",
} as const;

async function getStatus(): Promise<SubscriptionStatusResponse> {
  const response = await api.get<SubscriptionStatusResponse>(ENDPOINTS.status);
  return response.data;
}

async function createOrder(
  plan: SubscriptionPlan,
  couponCode?: string,
): Promise<SubscriptionOrderResponse> {
  const response = await api.post<SubscriptionOrderResponse>(ENDPOINTS.orders, {
    plan,
    couponCode: couponCode?.trim() || undefined,
  });
  return response.data;
}

async function verifyPayment(
  request: VerifySubscriptionPaymentRequest,
): Promise<SubscriptionStatusResponse> {
  const response = await api.post<SubscriptionStatusResponse>(ENDPOINTS.verify, request);
  return response.data;
}

/** Read-only: checks a coupon code without creating an order, for the "Apply" button's price preview. */
async function validateCoupon(code: string): Promise<CouponValidationResponse> {
  const response = await api.post<CouponValidationResponse>(ENDPOINTS.validateCoupon, { code });
  return response.data;
}

export const lawyerSubscriptionService = {
  getStatus,
  createOrder,
  verifyPayment,
  validateCoupon,
};
