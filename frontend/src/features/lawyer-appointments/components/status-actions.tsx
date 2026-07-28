"use client";

import { Check, CheckCheck, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { isAcceptable, isCompletable, isRejectable } from "@/lib/status";
import type { LawyerAppointmentAction } from "@/services/appointment-service";
import type { AppointmentResponse } from "@/types";

/**
 * The status transitions available on one appointment.
 *
 * Each button renders only when the backend would accept it - Accept and Reject
 * from PENDING, Complete from ACCEPTED - using the predicates in lib/status.ts,
 * which mirror AppointmentServiceImpl. No action is ever offered that is
 * guaranteed to answer 409.
 *
 * A terminal appointment renders nothing at all rather than disabled buttons:
 * greyed-out controls imply a temporary state, but these transitions will never
 * become available again.
 */
export function StatusActions({
  appointment,
  pendingAction,
  onAction,
}: {
  appointment: AppointmentResponse;
  /** The action currently in flight for THIS appointment, if any. */
  pendingAction: LawyerAppointmentAction | null;
  onAction: (appointment: AppointmentResponse, action: LawyerAppointmentAction) => void;
}) {
  const isBusy = pendingAction !== null;

  const canAccept = isAcceptable(appointment.status);
  const canReject = isRejectable(appointment.status);
  const canComplete = isCompletable(appointment.status);

  if (!canAccept && !canReject && !canComplete) return null;

  return (
    <>
      {canAccept ? (
        <Button
          size="sm"
          onClick={() => onAction(appointment, "accept")}
          disabled={isBusy}
          aria-busy={pendingAction === "accept"}
        >
          {pendingAction === "accept" ? <Spinner size="sm" /> : <Check aria-hidden />}
          Accept
        </Button>
      ) : null}

      {canReject ? (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onAction(appointment, "reject")}
          disabled={isBusy}
          aria-busy={pendingAction === "reject"}
        >
          {pendingAction === "reject" ? <Spinner size="sm" /> : <X aria-hidden />}
          Decline
        </Button>
      ) : null}

      {canComplete ? (
        <Button
          size="sm"
          onClick={() => onAction(appointment, "complete")}
          disabled={isBusy}
          aria-busy={pendingAction === "complete"}
        >
          {pendingAction === "complete" ? (
            <Spinner size="sm" />
          ) : (
            <CheckCheck aria-hidden />
          )}
          Mark completed
        </Button>
      ) : null}
    </>
  );
}
