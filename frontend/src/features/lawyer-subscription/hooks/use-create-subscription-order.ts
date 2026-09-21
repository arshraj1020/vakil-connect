"use client";

import { useMutation } from "@tanstack/react-query";

import { lawyerSubscriptionService } from "@/services/lawyer-subscription-service";
import type { SubscriptionOrderResponse, SubscriptionPlan } from "@/types";

/**
 * Creates a Razorpay order for a plan. No cache invalidation here - the row
 * this creates is PENDING and does not change what `useSubscriptionStatus`
 * should show until `useVerifySubscriptionPayment` actually activates it.
 */
export function useCreateSubscriptionOrder(options?: {
  onSuccess?: (order: SubscriptionOrderResponse) => void;
  onError?: (error: unknown) => void;
}) {
  return useMutation({
    mutationFn: (plan: SubscriptionPlan) => lawyerSubscriptionService.createOrder(plan),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
}
