"use client";

import { useMutation } from "@tanstack/react-query";

import { lawyerSubscriptionService } from "@/services/lawyer-subscription-service";
import type { CouponValidationResponse } from "@/types";

/**
 * Backs the "Apply" button on the subscription page: checks a coupon code
 * against the backend and, on success, hands back its discount percent so
 * the caller can show a live discounted price - without creating an order
 * or touching Razorpay.
 */
export function useValidateCoupon(options?: {
  onSuccess?: (result: CouponValidationResponse) => void;
  onError?: (error: unknown) => void;
}) {
  return useMutation({
    mutationFn: (code: string) => lawyerSubscriptionService.validateCoupon(code),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
}
