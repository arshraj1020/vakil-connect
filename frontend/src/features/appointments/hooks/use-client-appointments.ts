"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { isOnOrAfterToday } from "@/lib/date";
import { queryKeys } from "@/lib/query-keys";
import { appointmentService } from "@/services/appointment-service";
import { isActiveStatus } from "@/lib/status";
import type { AppointmentResponse } from "@/types";

/**
 * The signed-in client's appointments, plus the two views the dashboard needs.
 *
 * There is no "upcoming" or "recent" endpoint - the backend exposes a single
 * unpaged listing - so both are derived here from ONE request rather than
 * issuing two.
 *
 * The upcoming predicate deliberately mirrors the backend's own
 * `countByClientAndAppointmentDateGreaterThanEqualAndStatusIn`: dated today or
 * later AND still active. Matching it means the list can never contradict the
 * count rendered above it.
 *
 * Note this compares DATES, not timestamps, exactly as the backend does: an
 * appointment earlier today still counts as upcoming. Diverging would make the
 * widget disagree with the stat card.
 */
export function useClientAppointments() {
  const query = useQuery({
    queryKey: queryKeys.appointments.client(),
    queryFn: appointmentService.getClientAppointments,
  });

  const appointments = useMemo(() => query.data ?? [], [query.data]);

  const upcoming = useMemo(
    () =>
      appointments
        .filter(
          (appointment) =>
            isOnOrAfterToday(appointment.appointmentDate) &&
            isActiveStatus(appointment.status),
        )
        // The API sorts newest-first; upcoming reads better soonest-first.
        .slice()
        .sort(compareChronologically),
    [appointments],
  );

  const recent = useMemo(
    () =>
      appointments.filter(
        (appointment) =>
          !isOnOrAfterToday(appointment.appointmentDate) ||
          !isActiveStatus(appointment.status),
      ),
    [appointments],
  );

  return { ...query, appointments, upcoming, recent };
}

/** Ascending by date, then time. Both are ISO strings, so string order is correct. */
function compareChronologically(
  a: AppointmentResponse,
  b: AppointmentResponse,
): number {
  if (a.appointmentDate !== b.appointmentDate) {
    return a.appointmentDate < b.appointmentDate ? -1 : 1;
  }
  if (a.appointmentTime === b.appointmentTime) return 0;
  return a.appointmentTime < b.appointmentTime ? -1 : 1;
}
