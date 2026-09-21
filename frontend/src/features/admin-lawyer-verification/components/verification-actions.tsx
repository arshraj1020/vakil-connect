"use client";

import { BadgeCheck, Ban } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { isApiError, type LawyerProfileResponse } from "@/types";

import { useRejectLawyer } from "../hooks/use-reject-lawyer";
import { useVerifyLawyer } from "../hooks/use-verify-lawyer";

/**
 * The two actions the backend supports on a pending lawyer: Verify and
 * Decline.
 *
 * Decline is NOT the inverse of Verify and is not a delete. It sets
 * `rejected = true` (with an optional reason, shown back to the lawyer) and
 * the lawyer simply drops out of the pending queue - it is not terminal:
 * the lawyer's next profile edit clears the flag server-side and puts them
 * back in the queue automatically. There is deliberately no separate
 * "resubmit" action here for that reason.
 *
 * Owns its own mutation instances so each row tracks its own pending state; a
 * shared mutation would put every row in a loading state when one was clicked.
 *
 * The Verify confirmation states plainly that it "cannot currently be
 * reversed" (there is no un-verify endpoint) - that is still true and the
 * existence of Decline does not change it, since Decline only ever applies to
 * a lawyer that has not yet been verified.
 */
export function VerificationActions({
  lawyerId,
  lawyerName,
  onVerified,
  onRejected,
  className,
}: {
  lawyerId: string;
  lawyerName: string;
  /** Lets a parent close its dialog once the lawyer leaves the queue. */
  onVerified?: (lawyer: LawyerProfileResponse) => void;
  onRejected?: (lawyer: LawyerProfileResponse) => void;
  className?: string;
}) {
  const [isConfirmingVerify, setIsConfirmingVerify] = useState(false);
  const [isDeclining, setIsDeclining] = useState(false);
  const [declineReason, setDeclineReason] = useState("");

  const verifyMutation = useVerifyLawyer({
    onSuccess: (lawyer) => {
      setIsConfirmingVerify(false);

      toast.success(`${lawyer.fullName} is now verified`, {
        description:
          "Their profile is live in client search and they can accept bookings.",
      });

      onVerified?.(lawyer);
    },

    onError: (error) => {
      setIsConfirmingVerify(false);

      /*
       * 404 is the only documented failure: the lawyer id no longer resolves.
       * The service does not guard on current state, so verifying an
       * already-verified lawyer succeeds rather than conflicting - there is no
       * 409 path to handle here.
       */
      toast.error("Could not verify this lawyer", {
        description: isApiError(error)
          ? error.status === 404
            ? "This lawyer profile no longer exists."
            : error.message
          : "Please try again.",
      });
    },
  });

  const rejectMutation = useRejectLawyer({
    onSuccess: (lawyer) => {
      setIsDeclining(false);
      setDeclineReason("");

      toast.success(`${lawyer.fullName}'s application was declined`, {
        description: "They can resubmit by editing their profile.",
      });

      onRejected?.(lawyer);
    },

    onError: (error) => {
      toast.error("Could not decline this application", {
        description: isApiError(error)
          ? error.status === 404
            ? "This lawyer profile no longer exists."
            : error.message
          : "Please try again.",
      });
    },
  });

  return (
    <>
      <div className="flex items-center gap-2">
        <Button
          size="sm"
          onClick={() => setIsConfirmingVerify(true)}
          disabled={verifyMutation.isPending}
          className={className}
        >
          <BadgeCheck aria-hidden />
          Verify
        </Button>

        <Button
          size="sm"
          variant="outline"
          onClick={() => setIsDeclining(true)}
          disabled={rejectMutation.isPending}
        >
          <Ban aria-hidden />
          Decline
        </Button>
      </div>

      <ConfirmDialog
        open={isConfirmingVerify}
        onOpenChange={setIsConfirmingVerify}
        title={`Verify ${lawyerName}?`}
        description="This will verify the lawyer and make them visible on the platform. This action cannot currently be reversed."
        confirmLabel="Verify lawyer"
        isPending={verifyMutation.isPending}
        onConfirm={() => verifyMutation.mutate(lawyerId)}
      />

      <Dialog
        open={isDeclining}
        onOpenChange={rejectMutation.isPending ? undefined : setIsDeclining}
      >
        <DialogContent
          className="max-w-md"
          showClose={!rejectMutation.isPending}
        >
          <DialogHeader>
            <DialogTitle>Decline {lawyerName}&apos;s application?</DialogTitle>
            <DialogDescription>
              They&apos;ll drop out of the pending queue. This isn&apos;t
              final - editing their profile resubmits it automatically. A
              reason is optional but helps them fix what&apos;s wrong.
            </DialogDescription>
          </DialogHeader>

          <Textarea
            value={declineReason}
            onChange={(event) => setDeclineReason(event.target.value)}
            placeholder="Reason (optional) - e.g. bar council number could not be verified"
            rows={3}
            disabled={rejectMutation.isPending}
          />

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsDeclining(false)}
              disabled={rejectMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() =>
                rejectMutation.mutate({ lawyerId, reason: declineReason })
              }
              disabled={rejectMutation.isPending}
            >
              {rejectMutation.isPending ? <Spinner size="sm" /> : null}
              Decline application
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
