"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { queryKeys } from "@/lib/query-keys";
import { adminReviewService } from "@/services/admin-review-service";
import type { PageParams } from "@/types";

/**
 * One page of the platform's reviews.
 *
 * Uses the pre-existing `admin.reviews(params)` key. `keepPreviousData` holds
 * the current rows while the next page loads, so paging does not collapse the
 * list into a skeleton.
 *
 * Rows are passed through untouched - no sorting, no filtering, no grouping.
 * The endpoint emits no ORDER BY and accepts no filter, so any client-side
 * arrangement would either duplicate nothing or, worse, reorder a page against
 * the pagination it belongs to.
 */
export function useAdminReviews(params: PageParams) {
  const query = useQuery({
    queryKey: queryKeys.admin.reviews(params),
    queryFn: () => adminReviewService.getReviews(params),
    placeholderData: keepPreviousData,
  });

  const reviews = useMemo(() => query.data?.content ?? [], [query.data]);
  const page = query.data?.page;

  return {
    ...query,
    reviews,
    totalPages: page?.totalPages ?? 0,
    totalElements: page?.totalElements ?? 0,
  };
}
