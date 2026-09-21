"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { lawyerSubscriptionService } from "@/services/lawyer-subscription-service";
import type { SubscriptionStatusResponse, VerifySubscriptionPaymentRequest } from "@/types";

/** Verifies a completed Razorpay payment and activates the subscription. */
export function useVerifySubscriptionPayment(options?: {
  onSuccess?: (status: SubscriptionStatusResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: VerifySubscriptionPaymentRequest) =>
      lawyerSubscriptionService.verifyPayment(request),

    onSuccess: (status) => {
      queryClient.setQueryData(queryKeys.subscription.mine(), status);
      options?.onSuccess?.(status);
    },

    onError: (error) => options?.onError?.(error),
  });
}
