"use client";

import { CalendarX, ClipboardList } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { AppointmentDetailsDialog } from "@/features/appointments/components/appointment-details-dialog";
import { useLawyerAppointments } from "@/features/appointments/hooks/use-lawyer-appointments";
import { useUpdateAppointmentStatus } from "@/features/lawyer-appointments/hooks/use-update-appointment-status";
import {
  countByStatus,
  filterByStatus,
  type StatusFilter,
} from "@/features/lawyer-appointments/lib/filters";
import { formatDateLong, formatTime } from "@/lib/date";
import type { LawyerAppointmentAction } from "@/services/appointment-service";
import { isApiError, type AppointmentResponse } from "@/types";

import { AppointmentFilters } from "./appointment-filters";
import { AppointmentRow } from "./appointment-row";

/** Copy for the confirmation step, keyed by action. */
const ACTION_COPY: Record<
  LawyerAppointmentAction,
  { title: string; confirmLabel: string; describe: (name: string) => string; destructive: boolean; success: string }
> = {
  accept: {
    title: "Accept this consultation?",
    confirmLabel: "Accept",
    describe: (name) =>
      `${name} will be notified that you have confirmed the appointment.`,
    destructive: false,
    success: "Appointment accepted",
  },
  reject: {
    title: "Decline this consultation?",
    confirmLabel: "Decline",
    describe: (name) =>
      `${name} will be notified that you cannot take this appointment. This cannot be undone.`,
    destructive: true,
    success: "Appointment declined",
  },
  complete: {
    title: "Mark this consultation as completed?",
    confirmLabel: "Mark completed",
    describe: (name) =>
      `This closes the appointment with ${name} and allows them to leave a review.`,
    destructive: false,
    success: "Appointment completed",
  },
};

interface PendingAction {
  appointment: AppointmentResponse;
  action: LawyerAppointmentAction;
}

/**
 * The lawyer's appointment management screen.
 *
 * Filtering is client-side because `GET /api/lawyer/appointments` accepts no
 * query parameters and returns the full history unpaged. The filtered list and
 * the chip counts are memoised so typing elsewhere on the page cannot trigger a
 * re-filter of the whole list.
 *
 * Status changes are confirmed first, then applied without an optimistic
 * update: each transition can genuinely conflict (a client may cancel while the
 * lawyer is deciding), and reverting a badge is worse than a brief pending
 * state on the affected row.
 */
export function LawyerAppointmentsView() {
  const [filter, setFilter] = useState<StatusFilter>("all");
  const [details, setDetails] = useState<AppointmentResponse | null>(null);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [inFlight, setInFlight] = useState<PendingAction | null>(null);

  const { appointments, isPending, isError, error, refetch } =
    useLawyerAppointments();

  const counts = useMemo(() => countByStatus(appointments), [appointments]);
  const visible = useMemo(
    () => filterByStatus(appointments, filter),
    [appointments, filter],
  );

  const mutation = useUpdateAppointmentStatus({
    onSuccess: (appointment, action) => {
      setPending(null);
      setInFlight(null);

      toast.success(ACTION_COPY[action].success, {
        description: `${appointment.clientName} on ${formatDateLong(
          appointment.appointmentDate,
        )} at ${formatTime(appointment.appointmentTime)}.`,
      });
    },

    onError: (mutationError: unknown) => {
      setPending(null);
      setInFlight(null);

      /*
       * Both failure modes mean the list was stale, and the hook has already
       * refetched so the row will settle into its true state:
       *   409 - the client cancelled, or another action landed first
       *   404 - the appointment is gone, or was never this lawyer's
       */
      const description = isApiError(mutationError)
        ? mutationError.status === 409
          ? "This appointment's status changed before your action was applied."
          : mutationError.status === 404
            ? "This appointment is no longer available."
            : mutationError.message
        : "Please try again.";

      toast.error("Could not update the appointment", { description });
    },
  });

  const requestAction = (
    appointment: AppointmentResponse,
    action: LawyerAppointmentAction,
  ) => setPending({ appointment, action });

  const confirmAction = () => {
    if (!pending) return;

    setInFlight(pending);
    mutation.mutate({
      appointmentId: pending.appointment.id,
      action: pending.action,
    });
  };

  /** The action in flight for a given row, so only that row shows a spinner. */
  const pendingActionFor = (
    appointment: AppointmentResponse,
  ): LawyerAppointmentAction | null =>
    inFlight?.appointment.id === appointment.id ? inFlight.action : null;

  const copy = pending ? ACTION_COPY[pending.action] : null;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Appointments"
        description="Review requests, confirm consultations and close completed ones."
      />

      <AppointmentFilters value={filter} counts={counts} onChange={setFilter} />

      <Card>
        <CardContent className="p-5">
          {isPending ? (
            <ListSkeleton count={5} />
          ) : isError ? (
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load your appointments"
            />
          ) : appointments.length === 0 ? (
            <EmptyState
              icon={ClipboardList}
              title="No appointments yet"
              description="Consultation requests from clients will appear here."
            />
          ) : visible.length === 0 ? (
            <EmptyState
              icon={CalendarX}
              title="Nothing matches this filter"
              description="Choose a different status to see other appointments."
            />
          ) : (
            <ul className="space-y-3">
              {visible.map((appointment) => (
                <AppointmentRow
                  key={appointment.id}
                  appointment={appointment}
                  pendingAction={pendingActionFor(appointment)}
                  onViewDetails={setDetails}
                  onAction={requestAction}
                />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <AppointmentDetailsDialog
        appointment={details}
        open={details !== null}
        onOpenChange={(open) => {
          if (!open) setDetails(null);
        }}
        perspective="lawyer"
      />

      <ConfirmDialog
        open={pending !== null}
        onOpenChange={(open) => {
          if (!open) setPending(null);
        }}
        title={copy?.title ?? ""}
        description={
          pending && copy ? copy.describe(pending.appointment.clientName) : undefined
        }
        confirmLabel={copy?.confirmLabel ?? "Confirm"}
        destructive={copy?.destructive ?? false}
        isPending={mutation.isPending}
        onConfirm={confirmAction}
      />
    </div>
  );
}
