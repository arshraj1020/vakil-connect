import { APPOINTMENT_STATUS_META } from "@/lib/status";
import type { AppointmentResponse, AppointmentStatus } from "@/types";

/**
 * Client-side filtering for the lawyer's appointment list.
 *
 * `GET /api/lawyer/appointments` accepts no query parameters - it returns the
 * lawyer's entire history, unpaged - so filtering happens here. Pure functions,
 * memoised by the caller.
 */

/** "all" plus every real status. */
export type StatusFilter = "all" | AppointmentStatus;

/**
 * Filter options in workflow order rather than the enum's declaration order:
 * a lawyer works through pending requests first, so that sits nearest "All".
 * Labels come from the shared status map, so a status is never renamed in one
 * place and not another.
 */
export const STATUS_FILTERS: ReadonlyArray<{
  value: StatusFilter;
  label: string;
}> = [
  { value: "all", label: "All" },
  { value: "PENDING", label: APPOINTMENT_STATUS_META.PENDING.label },
  { value: "ACCEPTED", label: APPOINTMENT_STATUS_META.ACCEPTED.label },
  { value: "COMPLETED", label: APPOINTMENT_STATUS_META.COMPLETED.label },
  { value: "CANCELLED", label: APPOINTMENT_STATUS_META.CANCELLED.label },
  { value: "REJECTED", label: APPOINTMENT_STATUS_META.REJECTED.label },
];

export function filterByStatus(
  appointments: AppointmentResponse[],
  filter: StatusFilter,
): AppointmentResponse[] {
  if (filter === "all") return appointments;
  return appointments.filter((appointment) => appointment.status === filter);
}

/** How many appointments each filter would show, for the counts on the chips. */
export function countByStatus(
  appointments: AppointmentResponse[],
): Record<StatusFilter, number> {
  const counts: Record<StatusFilter, number> = {
    all: appointments.length,
    PENDING: 0,
    ACCEPTED: 0,
    COMPLETED: 0,
    CANCELLED: 0,
    REJECTED: 0,
  };

  for (const appointment of appointments) {
    counts[appointment.status] += 1;
  }

  return counts;
}
