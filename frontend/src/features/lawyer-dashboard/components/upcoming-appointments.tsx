"use client";

import { CalendarClock } from "lucide-react";

import { useLawyerAppointments } from "@/features/appointments/hooks/use-lawyer-appointments";
import { ROUTES } from "@/lib/routes";
import type { AppointmentResponse } from "@/types";

import { LawyerAppointmentList } from "./lawyer-appointment-list";

/**
 * Consultations after today, soonest first.
 *
 * Excludes today so it complements the schedule widget rather than repeating
 * its contents.
 */
export function UpcomingAppointments({
  onViewDetails,
}: {
  onViewDetails: (appointment: AppointmentResponse) => void;
}) {
  const { upcoming, isPending, isError, error, refetch } =
    useLawyerAppointments();

  return (
    <LawyerAppointmentList
      title="Upcoming"
      description="Confirmed and pending consultations after today."
      appointments={upcoming}
      isPending={isPending}
      isError={isError}
      error={error}
      onRetry={() => void refetch()}
      onViewDetails={onViewDetails}
      viewAllHref={ROUTES.LAWYER_APPOINTMENTS}
      emptyIcon={CalendarClock}
      emptyTitle="No upcoming consultations"
      emptyDescription="New requests from clients will appear here."
    />
  );
}
