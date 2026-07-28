"use client";

import { useState } from "react";

import { AppointmentDetailsDialog } from "@/features/appointments/components/appointment-details-dialog";
import type { AppointmentResponse } from "@/types";

import { LawyerStats } from "./lawyer-stats";
import { QuickActions } from "./quick-actions";
import { TodayAppointments } from "./today-appointments";
import { UpcomingAppointments } from "./upcoming-appointments";
import { WelcomeHeader } from "./welcome-header";

/**
 * Lawyer dashboard composition.
 *
 * Each widget owns its own loading, error and empty handling, so a failing
 * statistics request degrades one card rather than blanking the page.
 *
 * The details dialog is held here, once, rather than per widget: both lists can
 * open it, and a single instance means only one dialog can ever be mounted.
 */
export function LawyerDashboardView() {
  const [details, setDetails] = useState<AppointmentResponse | null>(null);

  return (
    <div className="space-y-8">
      <WelcomeHeader />

      <LawyerStats />

      <section className="space-y-4">
        <h2 className="text-lg font-semibold tracking-tight">Quick actions</h2>
        <QuickActions />
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <TodayAppointments onViewDetails={setDetails} />
        <UpcomingAppointments onViewDetails={setDetails} />
      </div>

      <AppointmentDetailsDialog
        appointment={details}
        open={details !== null}
        onOpenChange={(open) => {
          if (!open) setDetails(null);
        }}
        perspective="lawyer"
      />
    </div>
  );
}
