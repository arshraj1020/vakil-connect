"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import {
  selectPastAppointments,
  selectTodaysAppointments,
  selectTotalAppointments,
  selectUpcomingAppointments,
} from "@/features/lawyer-dashboard/lib/dashboard-utils";
import { queryKeys } from "@/lib/query-keys";
import { appointmentService } from "@/services/appointment-service";

/**
 * The signed-in lawyer's appointments, plus the views their screens need.
 *
 * Placed beside `useClientAppointments` rather than inside the dashboard
 * feature: Appointment Management needs the same list, and scoping it to the
 * dashboard would force either a cross-feature import from a dashboard folder
 * or a duplicate query.
 *
 * ONE request backs every derived view. There is no "today" or "upcoming"
 * endpoint - the backend exposes a single unpaged listing - so the splits are
 * memoised here rather than refetched per widget.
 */
export function useLawyerAppointments() {
  const query = useQuery({
    queryKey: queryKeys.appointments.lawyer(),
    queryFn: appointmentService.getLawyerAppointments,
  });

  const appointments = useMemo(() => query.data ?? [], [query.data]);

  const today = useMemo(
    () => selectTodaysAppointments(appointments),
    [appointments],
  );
  const upcoming = useMemo(
    () => selectUpcomingAppointments(appointments),
    [appointments],
  );
  const past = useMemo(
    () => selectPastAppointments(appointments),
    [appointments],
  );
  const total = useMemo(
    () => selectTotalAppointments(appointments),
    [appointments],
  );

  return { ...query, appointments, today, upcoming, past, total };
}
