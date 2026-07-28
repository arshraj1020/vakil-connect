"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { queryKeys } from "@/lib/query-keys";
import { adminLawyerService } from "@/services/admin-lawyer-service";
import type { PageParams } from "@/types";

/**
 * One page of the verification queue.
 *
 * Uses the pre-existing `admin.pendingLawyers(params)` key - the same key the
 * dashboard's preview card uses with `{page:0,size:5}`. They are separate cache
 * entries because the params differ, but both sit under `admin.pendingLawyers`,
 * so invalidating that prefix after a verification refreshes this screen AND
 * the dashboard preview in one call.
 *
 * `keepPreviousData` holds the current page while the next loads, so paging
 * does not collapse the table into a skeleton.
 */
export function usePendingLawyers(params: PageParams) {
  const query = useQuery({
    queryKey: queryKeys.admin.pendingLawyers(params),
    queryFn: () => adminLawyerService.getPendingLawyers(params),
    placeholderData: keepPreviousData,
  });

  const lawyers = useMemo(() => query.data?.content ?? [], [query.data]);
  const page = query.data?.page;

  return {
    ...query,
    lawyers,
    totalPages: page?.totalPages ?? 0,
    totalElements: page?.totalElements ?? 0,
  };
}

/**
 * The full profile behind one queue row, fetched only when a dialog opens.
 *
 * The queue returns `LawyerSummaryResponse`, which omits the fields
 * verification actually turns on - bar council number, email, bio, office
 * address. Those live on `LawyerProfileResponse`, reachable through the public
 * detail endpoint, which resolves unverified lawyers because it uses a plain
 * `findById`.
 *
 * Keyed at `lawyers.detail(id)`, the SAME entry the public profile page uses.
 * That is intentional: it is the same resource, so an admin who has already
 * viewed that lawyer gets a cache hit, and verifying invalidates one key rather
 * than two copies of the same data.
 */
export function useLawyerForReview(lawyerId: string | null) {
  return useQuery({
    queryKey: queryKeys.lawyers.detail(lawyerId ?? ""),
    queryFn: () => adminLawyerService.getLawyerForReview(lawyerId ?? ""),
    enabled: Boolean(lawyerId),
  });
}
