"use client";

import { CalendarClock, History, Search } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useCancelAppointment } from "@/features/appointments/hooks/use-cancel-appointment";
import { useClientAppointments } from "@/features/appointments/hooks/use-client-appointments";
import { formatDateLong, formatTime } from "@/lib/date";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { isApiError, type AppointmentResponse } from "@/types";

import { AppointmentDetailsDialog } from "./appointment-details-dialog";
import { ClientAppointmentRow } from "./client-appointment-row";

type Tab = "upcoming" | "past";

/**
 * The client's appointments.
 *
 * Split into Upcoming and Past rather than paginated: `GET
 * /api/client/appointments` returns a plain array with no paging, and slicing
 * it client-side would add controls without saving a single byte of transfer.
 * The two views reuse the derived split in `useClientAppointments`, whose
 * "upcoming" predicate mirrors the backend's own.
 *
 * Cancellation tracks the in-flight row locally so only that row shows a
 * pending state - a page-wide spinner would suggest the whole list is
 * unavailable when one action is running.
 */
export function ClientAppointmentsView() {
  const [tab, setTab] = useState<Tab>("upcoming");
  const [details, setDetails] = useState<AppointmentResponse | null>(null);
  const [pendingCancel, setPendingCancel] = useState<AppointmentResponse | null>(
    null,
  );
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const { upcoming, recent, isPending, isError, error, refetch } =
    useClientAppointments();

  const cancelMutation = useCancelAppointment({
    onSuccess: (appointment) => {
      setPendingCancel(null);
      setCancellingId(null);
      toast.success("Appointment cancelled", {
        description: `${appointment.lawyerName} on ${formatDateLong(
          appointment.appointmentDate,
        )}.`,
      });
    },
    onError: (mutationError: unknown) => {
      setPendingCancel(null);
      setCancellingId(null);

      /*
       * Both failure modes here mean the list was stale, and the hook has
       * already triggered a refetch so the row will settle into its real state:
       *   409 - the lawyer completed or rejected it first
       *   404 - it is gone (or was never this client's)
       */
      const message = isApiError(mutationError)
        ? mutationError.status === 409
          ? "This appointment can no longer be cancelled - its status changed."
          : mutationError.status === 404
            ? "This appointment is no longer available."
            : mutationError.message
        : "Could not cancel this appointment. Please try again.";

      toast.error("Cancellation failed", { description: message });
    },
  });

  const confirmCancel = () => {
    if (!pendingCancel) return;
    setCancellingId(pendingCancel.id);
    cancelMutation.mutate(pendingCancel.id);
  };

  const appointments = tab === "upcoming" ? upcoming : recent;

  const tabs: Array<{ id: Tab; label: string; count: number }> = [
    { id: "upcoming", label: "Upcoming", count: upcoming.length },
    { id: "past", label: "Past", count: recent.length },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="My appointments"
        description="Track your consultation requests and history."
        actions={
          <Button asChild size="sm">
            <Link href={ROUTES.LAWYERS}>
              <Search aria-hidden />
              Find a lawyer
            </Link>
          </Button>
        }
      />

      {/* Tabs */}
      <div
        role="tablist"
        aria-label="Appointment status"
        className="inline-flex rounded-lg border border-border p-1"
      >
        {tabs.map((entry) => {
          const selected = tab === entry.id;

          return (
            <button
              key={entry.id}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => setTab(entry.id)}
              className={cn(
                "rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                selected
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {entry.label}
              {!isPending ? (
                <span className="ml-1.5 text-xs opacity-75">{entry.count}</span>
              ) : null}
            </button>
          );
        })}
      </div>

      <Card>
        <CardContent className="p-5">
          {isPending ? (
            <ListSkeleton count={4} />
          ) : isError ? (
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load your appointments"
            />
          ) : appointments.length === 0 ? (
            tab === "upcoming" ? (
              <EmptyState
                icon={CalendarClock}
                title="No upcoming appointments"
                description="Book a consultation with a verified lawyer to get started."
                action={
                  <Button asChild size="sm">
                    <Link href={ROUTES.LAWYERS}>Find a lawyer</Link>
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={History}
                title="No past appointments"
                description="Completed and cancelled consultations will appear here."
              />
            )
          ) : (
            <div className="space-y-3">
              {appointments.map((appointment) => (
                <ClientAppointmentRow
                  key={appointment.id}
                  appointment={appointment}
                  onViewDetails={setDetails}
                  onCancel={setPendingCancel}
                  isCancelling={cancellingId === appointment.id}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AppointmentDetailsDialog
        appointment={details}
        open={details !== null}
        onOpenChange={(open) => {
          if (!open) setDetails(null);
        }}
      />

      <ConfirmDialog
        open={pendingCancel !== null}
        onOpenChange={(open) => {
          if (!open) setPendingCancel(null);
        }}
        title="Cancel this appointment?"
        description={
          pendingCancel
            ? `Your consultation with ${pendingCancel.lawyerName} on ${formatDateLong(
                pendingCancel.appointmentDate,
              )} at ${formatTime(pendingCancel.appointmentTime)} will be cancelled. This cannot be undone.`
            : undefined
        }
        confirmLabel="Cancel appointment"
        cancelLabel="Keep appointment"
        destructive
        isPending={cancelMutation.isPending}
        onConfirm={confirmCancel}
      />
    </div>
  );
}
