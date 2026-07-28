"use client";

import { CalendarCheck, CalendarDays, CheckCircle2, Clock } from "lucide-react";

import { ErrorState } from "@/components/common/error-state";
import { StatCardSkeleton } from "@/components/common/loading-skeleton";
import { StatCard } from "@/components/common/stat-card";
import { useLawyerAppointments } from "@/features/appointments/hooks/use-lawyer-appointments";
import { useLawyerDashboard } from "@/features/lawyer-dashboard/hooks/use-lawyer-dashboard";

/**
 * Headline counts.
 *
 * Pending, accepted and completed come from `GET /api/lawyer/dashboard` and are
 * authoritative - never recomputed here.
 *
 * The grand total is the one figure that endpoint cannot express: it omits
 * rejected and cancelled counts, so they cannot be summed. It is therefore
 * derived from the appointment list, which is unpaged and already fetched for
 * the widgets below - no additional request.
 */
export function LawyerStats() {
  const stats = useLawyerDashboard();
  const { total, isPending: listPending } = useLawyerAppointments();

  if (stats.isPending) return <StatCardSkeleton />;

  if (stats.isError) {
    return (
      <ErrorState
        error={stats.error}
        onRetry={() => void stats.refetch()}
        title="Could not load your statistics"
      />
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard
        label="Total appointments"
        value={listPending ? "-" : total}
        icon={CalendarDays}
        hint="All time, every status"
      />
      <StatCard
        label="Pending"
        value={stats.data.pendingAppointments}
        icon={Clock}
        hint="Awaiting your response"
      />
      <StatCard
        label="Confirmed"
        value={stats.data.acceptedAppointments}
        icon={CalendarCheck}
      />
      <StatCard
        label="Completed"
        value={stats.data.completedAppointments}
        icon={CheckCircle2}
      />
    </div>
  );
}
