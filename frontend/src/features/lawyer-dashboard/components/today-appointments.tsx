"use client";

import { CalendarCheck } from "lucide-react";

import { useLawyerAppointments } from "@/features/appointments/hooks/use-lawyer-appointments";
import { ROUTES } from "@/lib/routes";
import type { AppointmentResponse } from "@/types";

import { LawyerAppointmentList } from "./lawyer-appointment-list";

/**
 * Today's schedule, earliest first.
 *
 * The predicate matches the backend's own today count (dated today AND still
 * pending or accepted), so this list can never disagree with the statistic
 * above it.
 */
export function TodayAppointments({
  onViewDetails,
}: {
  onViewDetails: (appointment: AppointmentResponse) => void;
}) {
  const { today, isPending, isError, error, refetch } = useLawyerAppointments();

  return (
    <LawyerAppointmentList
      title="Today's schedule"
      description="Consultations booked for today."
      appointments={today}
      isPending={isPending}
      isError={isError}
      error={error}
      onRetry={() => void refetch()}
      onViewDetails={onViewDetails}
      viewAllHref={ROUTES.LAWYER_APPOINTMENTS}
      emptyIcon={CalendarCheck}
      emptyTitle="Nothing scheduled today"
      emptyDescription="Consultations booked for today will appear here."
    />
  );
}
