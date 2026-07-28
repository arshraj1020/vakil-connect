"use client";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { isCancellable } from "@/lib/status";
import { cn } from "@/lib/utils";
import type { AppointmentResponse } from "@/types";

import { AppointmentCard } from "./appointment-card";

/**
 * An appointment in the client's list, with its actions.
 *
 * Wraps the shared AppointmentCard rather than replacing it: the card stays
 * purely presentational and reusable (the dashboard renders it with no
 * actions), while the row adds behaviour.
 *
 * Cancel renders only when `isCancellable` allows it - PENDING or ACCEPTED -
 * which mirrors the backend rule exactly, so the button is never offered for an
 * action that would return 409.
 */
export function ClientAppointmentRow({
  appointment,
  onViewDetails,
  onCancel,
  isCancelling = false,
}: {
  appointment: AppointmentResponse;
  onViewDetails: (appointment: AppointmentResponse) => void;
  onCancel: (appointment: AppointmentResponse) => void;
  isCancelling?: boolean;
}) {
  const canCancel = isCancellable(appointment.status);

  return (
    <AppointmentCard
      appointment={appointment}
      perspective="client"
      className={cn(
        "transition-opacity",
        // Only the row being cancelled dims; the rest stay usable.
        isCancelling && "pointer-events-none opacity-60",
      )}
      actions={
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onViewDetails(appointment)}
          >
            Details
          </Button>

          {canCancel ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => onCancel(appointment)}
              disabled={isCancelling}
              aria-busy={isCancelling}
            >
              {isCancelling ? <Spinner size="sm" /> : null}
              Cancel
            </Button>
          ) : null}
        </div>
      }
    />
  );
}
