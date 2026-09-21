"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { adminLawyerService } from "@/services/admin-lawyer-service";
import type { LawyerProfileResponse } from "@/types";

/**
 * Declines one lawyer's pending application.
 *
 * Mirrors useVerifyLawyer's invalidation exactly, for the same reason: a
 * rejection also removes the lawyer from the pending queue (it now fails the
 * `rejected = false` half of the query), and the dashboard's
 * unverifiedLawyers / pendingVerification counters both change.
 *
 * NOT invalidated: `lawyers.all + "search"` - rejection can never add a
 * lawyer to public search (that requires `verified = true`, untouched here),
 * unlike verifyLawyer which must invalidate it.
 */
export function useRejectLawyer(options?: {
  onSuccess?: (lawyer: LawyerProfileResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ lawyerId, reason }: { lawyerId: string; reason?: string }) =>
      adminLawyerService.rejectLawyer(lawyerId, reason),

    onSuccess: (lawyer) => {
      queryClient.setQueryData(queryKeys.lawyers.detail(lawyer.id), lawyer);

      void queryClient.invalidateQueries({
        queryKey: [...queryKeys.admin.all, "pending-lawyers"],
      });

      void queryClient.invalidateQueries({
        queryKey: queryKeys.dashboard.admin(),
      });

      options?.onSuccess?.(lawyer);
    },

    onError: (error) => options?.onError?.(error),
  });
}
