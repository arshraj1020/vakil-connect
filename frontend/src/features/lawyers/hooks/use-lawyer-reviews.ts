"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { queryKeys } from "@/lib/query-keys";
import { lawyerService } from "@/services/lawyer-service";
import type { PageParams } from "@/types";

/**
 * Paged reviews for a lawyer.
 *
 * The key nests under `lawyers.detail(id)`, so invalidating a lawyer also drops
 * their cached reviews - which matters once a client can post one.
 */
export function useLawyerReviews(lawyerId: string, page = 0) {
  const params: PageParams = { page, size: DEFAULT_PAGE_SIZE };

  return useQuery({
    queryKey: queryKeys.lawyers.reviews(lawyerId, params),
    queryFn: () => lawyerService.getLawyerReviews(lawyerId, params),
    enabled: Boolean(lawyerId),
    placeholderData: keepPreviousData,
  });
}
