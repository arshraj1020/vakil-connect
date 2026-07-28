"use client";

import { CalendarClock, Search } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { useClientAppointments } from "@/features/appointments/hooks/use-client-appointments";
import { ROUTES } from "@/lib/routes";

import { AppointmentListCard } from "./appointment-list-card";

/**
 * Consultations still to come, soonest first.
 *
 * Reads the `upcoming` view derived in `useClientAppointments`, whose predicate
 * mirrors the backend's own upcoming count - so this list can never contradict
 * the statistic shown above it.
 */
export function UpcomingAppointments() {
  const { upcoming, isPending, isError, error, refetch } = useClientAppointments();

  return (
    <AppointmentListCard
      title="Upcoming consultations"
      description="Your next scheduled appointments."
      appointments={upcoming}
      isPending={isPending}
      isError={isError}
      error={error}
      onRetry={() => void refetch()}
      viewAllHref={ROUTES.CLIENT_APPOINTMENTS}
      emptyIcon={CalendarClock}
      emptyTitle="No upcoming consultations"
      emptyDescription="Find a verified lawyer and book your first consultation."
      emptyAction={
        <Button asChild size="sm">
          <Link href={ROUTES.LAWYERS}>
            <Search aria-hidden />
            Find a lawyer
          </Link>
        </Button>
      }
    />
  );
}
