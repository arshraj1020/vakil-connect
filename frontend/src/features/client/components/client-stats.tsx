"use client";

import { Ban, CalendarClock, CalendarDays, CheckCircle2 } from "lucide-react";

import { ErrorState } from "@/components/common/error-state";
import { StatCardSkeleton } from "@/components/common/loading-skeleton";
import { StatCard } from "@/components/common/stat-card";
import { useClientDashboard } from "@/features/appointments/hooks/use-client-dashboard";

/**
 * Headline counts.
 *
 * Values come straight from `GET /api/client/dashboard` and are never derived
 * from the appointment list - the server's definition of "upcoming" is the
 * authoritative one, and recomputing it here would eventually disagree.
 */
export function ClientStats() {
  const { data, isPending, isError, error, refetch } = useClientDashboard();

  if (isPending) return <StatCardSkeleton />;

  if (isError) {
    return (
      <ErrorState
        error={error}
        onRetry={() => void refetch()}
        title="Could not load your statistics"
      />
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard
        label="Total appointments"
        value={data.totalAppointments}
        icon={CalendarDays}
      />
      <StatCard
        label="Upcoming"
        value={data.upcomingAppointments}
        icon={CalendarClock}
        hint="Scheduled or awaiting confirmation"
      />
      <StatCard
        label="Completed"
        value={data.completedAppointments}
        icon={CheckCircle2}
      />
      <StatCard
        label="Cancelled"
        value={data.cancelledAppointments}
        icon={Ban}
      />
    </div>
  );
}
