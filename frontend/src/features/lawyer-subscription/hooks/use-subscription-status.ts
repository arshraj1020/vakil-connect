"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { lawyerSubscriptionService } from "@/services/lawyer-subscription-service";

/** The authenticated lawyer's current subscription state. */
export function useSubscriptionStatus() {
  return useQuery({
    queryKey: queryKeys.subscription.mine(),
    queryFn: lawyerSubscriptionService.getStatus,
  });
}
