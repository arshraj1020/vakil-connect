"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { groupByDay, hasAnyAvailability } from "@/lib/availability";
import { queryKeys } from "@/lib/query-keys";
import { lawyerService } from "@/services/lawyer-service";

/**
 * A lawyer's weekly availability, grouped for display.
 *
 * Requested only on the profile page, never per card: availability is absent
 * from `LawyerSummaryResponse`, so showing it in search results would mean one
 * request per card - ten extra round-trips on a single page of results.
 */
export function useLawyerAvailability(lawyerId: string) {
  const query = useQuery({
    queryKey: queryKeys.lawyers.availability(lawyerId),
    queryFn: () => lawyerService.getLawyerAvailability(lawyerId),
    enabled: Boolean(lawyerId),
  });

  const windows = useMemo(() => query.data ?? [], [query.data]);
  const days = useMemo(() => groupByDay(windows), [windows]);
  const isBookable = useMemo(() => hasAnyAvailability(windows), [windows]);

  return { ...query, windows, days, isBookable };
}
