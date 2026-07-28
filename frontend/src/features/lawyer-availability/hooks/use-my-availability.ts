"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { groupByDay, hasAnyAvailability } from "@/lib/availability";
import { queryKeys } from "@/lib/query-keys";
import { availabilityService } from "@/services/availability-service";

/**
 * The signed-in lawyer's own availability, grouped for display.
 *
 * Named `useMyAvailability` rather than `useLawyerAvailability` because that
 * name is already taken by the PUBLIC hook in features/lawyers, which takes a
 * lawyerId. Two hooks with the same name and different shapes would be a
 * genuine footgun.
 *
 * Grouping reuses `lib/availability.ts`, the same helper the public profile
 * uses, so weekday ordering and window formatting are defined once.
 */
export function useMyAvailability() {
  const query = useQuery({
    queryKey: queryKeys.availability.mine(),
    queryFn: availabilityService.getMyAvailability,
  });

  const windows = useMemo(() => query.data ?? [], [query.data]);
  const days = useMemo(() => groupByDay(windows), [windows]);
  const hasAvailability = useMemo(() => hasAnyAvailability(windows), [windows]);

  return { ...query, windows, days, hasAvailability };
}
