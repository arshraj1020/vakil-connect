"use client";

import { History } from "lucide-react";

import { useClientAppointments } from "@/features/appointments/hooks/use-client-appointments";
import { ROUTES } from "@/lib/routes";

import { AppointmentListCard } from "./appointment-list-card";

/**
 * Past and closed consultations, most recent first.
 *
 * The complement of the upcoming view: anything already dated in the past, or
 * in a terminal state (completed, cancelled, declined). The API already sorts
 * newest-first, so no re-sorting is needed here.
 */
export function RecentAppointments() {
  const { recent, isPending, isError, error, refetch } = useClientAppointments();

  return (
    <AppointmentListCard
      title="Recent activity"
      description="Consultations that have concluded or were closed."
      appointments={recent}
      isPending={isPending}
      isError={isError}
      error={error}
      onRetry={() => void refetch()}
      viewAllHref={ROUTES.CLIENT_APPOINTMENTS}
      emptyIcon={History}
      emptyTitle="Nothing here yet"
      emptyDescription="Your completed and cancelled consultations will appear here."
    />
  );
}
