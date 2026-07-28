"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { adminLawyerService } from "@/services/admin-lawyer-service";
import type { LawyerProfileResponse } from "@/types";

/**
 * Verifies one lawyer.
 *
 * No optimistic update. Verification is irreversible - there is no un-verify
 * endpoint - so showing it as done before the server confirms would be a claim
 * the UI could not retract if the request failed.
 *
 * There is no bulk variant because the API has no bulk endpoint. Looping N PUTs
 * behind one button would give N independent failure points and no way to
 * report a partial result honestly, so the UI verifies one lawyer at a time.
 *
 * Invalidation covers exactly what this write makes stale, and nothing else:
 *
 *   admin.pendingLawyers      the lawyer leaves the queue - the prefix is
 *                             invalidated, so every cached page AND the
 *                             dashboard's 5-row preview refresh together
 *   dashboard.admin()         verifiedLawyers and unverifiedLawyers both change
 *   lawyers.detail(id)        this lawyer's `verified` flag flipped; the
 *                             response is written straight in rather than
 *                             refetched, since the PUT already returned it
 *   lawyers.all + "search"    a verified lawyer now appears in public search,
 *                             which hardcodes `verified = true`
 *
 * NOT invalidated: users, reviews, appointments and availability, none of which
 * a verification touches.
 */
export function useVerifyLawyer(options?: {
  onSuccess?: (lawyer: LawyerProfileResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (lawyerId: string) => adminLawyerService.verifyLawyer(lawyerId),

    onSuccess: (lawyer) => {
      // The PUT returns the updated profile, so the detail cache is corrected
      // directly instead of being invalidated and refetched.
      queryClient.setQueryData(queryKeys.lawyers.detail(lawyer.id), lawyer);

      void queryClient.invalidateQueries({
        queryKey: [...queryKeys.admin.all, "pending-lawyers"],
      });

      void queryClient.invalidateQueries({
        queryKey: queryKeys.dashboard.admin(),
      });

      void queryClient.invalidateQueries({
        queryKey: [...queryKeys.lawyers.all, "search"],
      });

      options?.onSuccess?.(lawyer);
    },

    onError: (error) => options?.onError?.(error),
  });
}
