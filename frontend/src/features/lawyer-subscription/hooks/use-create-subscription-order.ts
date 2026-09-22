"use client";

import { useMutation } from "@tanstack/react-query";

import { lawyerSubscriptionService } from "@/services/lawyer-subscription-service";
import type { SubscriptionOrderResponse, SubscriptionPlan } from "@/types";

/**
 * Creates a Razorpay order for a plan (optionally discounted by a coupon
 * code). No cache invalidation here for the paid path - the row this creates
 * is PENDING and does not change what `useSubscriptionStatus` should show
 * until `useVerifySubscriptionPayment` actually activates it. A 100%-off
 * coupon is the exception: the backend activates it immediately, so the
 * caller (`SubscriptionView`) is responsible for refetching status itself
 * when `requiresPayment` comes back false.
 */
export function useCreateSubscriptionOrder(options?: {
  onSuccess?: (order: SubscriptionOrderResponse) => void;
  onError?: (error: unknown) => void;
}) {
  return useMutation({
    mutationFn: ({ plan, couponCode }: { plan: SubscriptionPlan; couponCode?: string }) =>
      lawyerSubscriptionService.createOrder(plan, couponCode),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
}
