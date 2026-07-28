"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { appointmentService } from "@/services/appointment-service";

/**
 * Client dashboard statistics.
 *
 * The counts are computed server-side and treated as authoritative - they are
 * never recalculated from the appointment list, which would drift as soon as
 * the two definitions of "upcoming" diverged.
 */
export function useClientDashboard() {
  return useQuery({
    queryKey: queryKeys.dashboard.client(),
    queryFn: appointmentService.getClientDashboard,
  });
}
