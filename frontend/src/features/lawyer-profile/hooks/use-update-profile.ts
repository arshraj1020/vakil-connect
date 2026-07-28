"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { lawyerProfileService } from "@/services/lawyer-profile-service";
import type { LawyerProfileResponse, UpdateLawyerProfileRequest } from "@/types";

/**
 * Saves the profile.
 *
 * The response IS the updated profile, so it is written straight into the cache
 * with `setQueryData` instead of triggering a refetch - the form can then reset
 * to the server's own copy without a round-trip or an intermediate stale render.
 *
 * Invalidation is limited to profile-related queries:
 *
 *   lawyers.myProfile()      - replaced directly, above
 *   lawyers.detail(id)       - the public view of this same lawyer, whose bio,
 *                              fee, city and specializations just changed
 *
 * Deliberately NOT invalidated: `lawyers.search`, which is a different concern
 * and would refetch every cached filter combination; and appointments and
 * availability, which no profile field touches.
 */
export function useUpdateProfile(options?: {
  onSuccess?: (profile: LawyerProfileResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpdateLawyerProfileRequest) =>
      lawyerProfileService.updateMyProfile(payload),

    onSuccess: (profile) => {
      queryClient.setQueryData(queryKeys.lawyers.myProfile(), profile);

      void queryClient.invalidateQueries({
        queryKey: queryKeys.lawyers.detail(profile.id),
      });

      options?.onSuccess?.(profile);
    },

    onError: (error) => options?.onError?.(error),
  });
}
