"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { appointmentService } from "@/services/appointment-service";

/**
 * Lawyer dashboard statistics.
 *
 * Counts are computed server-side and treated as authoritative - they are never
 * recalculated from the appointment list, which would drift the moment the two
 * definitions of "today's" or "pending" diverged.
 *
 * The one exception is the grand total, which this endpoint cannot express (it
 * omits rejected and cancelled) and which is therefore derived from the list in
 * `useLawyerAppointments`.
 */
export function useLawyerDashboard() {
  return useQuery({
    queryKey: queryKeys.dashboard.lawyer(),
    queryFn: appointmentService.getLawyerDashboard,
  });
}
