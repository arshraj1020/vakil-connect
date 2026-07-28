"use client";

import { ClientStats } from "./client-stats";
import { QuickActions } from "./quick-actions";
import { RecentAppointments } from "./recent-appointments";
import { UpcomingAppointments } from "./upcoming-appointments";
import { WelcomeHeader } from "./welcome-header";

/**
 * Dashboard composition.
 *
 * Each widget owns its own loading, error and empty handling rather than the
 * page gating on every query at once: a failing statistics request should
 * degrade one card, not blank the whole screen.
 */
export function ClientDashboardView() {
  return (
    <div className="space-y-8">
      <WelcomeHeader />

      <ClientStats />

      <section className="space-y-4">
        <h2 className="text-lg font-semibold tracking-tight">Quick actions</h2>
        <QuickActions />
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <UpcomingAppointments />
        <RecentAppointments />
      </div>
    </div>
  );
}
