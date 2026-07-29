"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { clientProfileService } from "@/services/client-profile-service";

/**
 * The signed-in client's account.
 *
 * Keyed at `auth.currentUser()` - the pre-existing key for "the caller's own
 * account record". `GET /api/client/profile` and `GET /api/users/me` return the
 * identical `CurrentUserResponse` for the same person, so caching them
 * separately would create two entries for one resource that could drift.
 *
 * Note the session in the Zustand store is NOT this cache. The store is
 * populated once at hydration and read synchronously by guards and navigation;
 * this query is the editable view of the same record. `useUpdateClientProfile`
 * keeps the two in step after a write.
 */
export function useMyClientProfile() {
  const query = useQuery({
    queryKey: queryKeys.auth.currentUser(),
    queryFn: clientProfileService.getMyProfile,
  });

  return { ...query, profile: query.data };
}
