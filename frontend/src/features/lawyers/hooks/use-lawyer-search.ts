"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { lawyerService } from "@/services/lawyer-service";
import type { LawyerSearchParams } from "@/types";

/**
 * Paged lawyer search.
 *
 * `placeholderData: keepPreviousData` keeps the previous page on screen while
 * the next one loads. Without it, paging or adjusting a filter unmounts the
 * results and collapses the page height, which makes the layout jump and loses
 * the user's scroll position on every interaction.
 *
 * The filters object is part of the query key, so each distinct search is
 * cached separately and returning to a previous filter combination is instant.
 */
export function useLawyerSearch(filters: LawyerSearchParams) {
  return useQuery({
    queryKey: queryKeys.lawyers.search(filters),
    queryFn: () => lawyerService.searchLawyers(filters),
    placeholderData: keepPreviousData,
  });
}
