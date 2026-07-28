"use client";

import { Button } from "@/components/ui/button";
import { AppointmentCard } from "@/features/appointments/components/appointment-card";
import { cn } from "@/lib/utils";
import type { LawyerAppointmentAction } from "@/services/appointment-service";
import type { AppointmentResponse } from "@/types";

import { StatusActions } from "./status-actions";

/**
 * One appointment in the lawyer's list.
 *
 * Wraps the shared AppointmentCard rather than replacing it - the card stays
 * presentational and is reused by the dashboard and the client portal - and
 * supplies the lawyer's actions through its existing `actions` slot.
 */
export function AppointmentRow({
  appointment,
  pendingAction,
  onViewDetails,
  onAction,
}: {
  appointment: AppointmentResponse;
  pendingAction: LawyerAppointmentAction | null;
  onViewDetails: (appointment: AppointmentResponse) => void;
  onAction: (appointment: AppointmentResponse, action: LawyerAppointmentAction) => void;
}) {
  return (
    <li className="list-none">
      <AppointmentCard
        appointment={appointment}
        perspective="lawyer"
        className={cn(
          "transition-opacity",
          // Only the row being acted on dims; the rest stay usable.
          pendingAction !== null && "pointer-events-none opacity-60",
        )}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onViewDetails(appointment)}
            >
              Details
            </Button>

            <StatusActions
              appointment={appointment}
              pendingAction={pendingAction}
              onAction={onAction}
            />
          </div>
        }
      />
    </li>
  );
}
