"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { lawyerProfileService } from "@/services/lawyer-profile-service";
import { isApiError } from "@/types";

/**
 * The signed-in lawyer's own profile.
 *
 * Uses the pre-existing `lawyers.myProfile()` key, which is deliberately NOT
 * nested under `lawyers.detail(id)`: this profile is fetched without an id, so
 * it cannot live under one, and invalidating a public detail must not clobber
 * it.
 *
 * A 404 means the account has no lawyer row - a real state the UI renders
 * distinctly - so it is surfaced rather than retried.
 */
export function useMyProfile() {
  const query = useQuery({
    queryKey: queryKeys.lawyers.myProfile(),
    queryFn: lawyerProfileService.getMyProfile,
    retry: (failureCount, error) =>
      isApiError(error) && error.status === 404 ? false : failureCount < 2,
  });

  const isMissingProfile = isApiError(query.error) && query.error.status === 404;

  return { ...query, profile: query.data, isMissingProfile };
}
