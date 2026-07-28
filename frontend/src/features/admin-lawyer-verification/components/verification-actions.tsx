"use client";

import { BadgeCheck } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { Button } from "@/components/ui/button";
import { isApiError, type LawyerProfileResponse } from "@/types";

import { useVerifyLawyer } from "../hooks/use-verify-lawyer";

/**
 * The only action the backend supports on a pending lawyer.
 *
 * There is no Reject or Decline control - not even a disabled one. No reject
 * endpoint exists, and no un-verify endpoint exists, so a disabled button would
 * advertise a workflow the product does not have. An application the admin does
 * not want to approve is simply left in the queue, which is exactly what the
 * backend models.
 *
 * Owns its own mutation instance so each row tracks its own pending state; a
 * shared mutation would put every row in a loading state when one was clicked.
 *
 * The confirmation step exists because `verifyLawyer` sets `verified = true`
 * with no inverse operation anywhere in the API. The copy states that plainly
 * and stops there - "cannot currently be reversed" is the fact an admin needs,
 * and dressing it up would misrepresent a routine approval as a hazard.
 */
export function VerificationActions({
  lawyerId,
  lawyerName,
  onVerified,
  className,
}: {
  lawyerId: string;
  lawyerName: string;
  /** Lets a parent close its dialog once the lawyer leaves the queue. */
  onVerified?: (lawyer: LawyerProfileResponse) => void;
  className?: string;
}) {
  const [isConfirming, setIsConfirming] = useState(false);

  const mutation = useVerifyLawyer({
    onSuccess: (lawyer) => {
      setIsConfirming(false);

      toast.success(`${lawyer.fullName} is now verified`, {
        description:
          "Their profile is live in client search and they can accept bookings.",
      });

      onVerified?.(lawyer);
    },

    onError: (error) => {
      setIsConfirming(false);

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

  return (
    <>
      <Button
        size="sm"
        onClick={() => setIsConfirming(true)}
        disabled={mutation.isPending}
        className={className}
      >
        <BadgeCheck aria-hidden />
        Verify
      </Button>

      <ConfirmDialog
        open={isConfirming}
        onOpenChange={setIsConfirming}
        title={`Verify ${lawyerName}?`}
        description="This will verify the lawyer and make them visible on the platform. This action cannot currently be reversed."
        confirmLabel="Verify lawyer"
        isPending={mutation.isPending}
        onConfirm={() => mutation.mutate(lawyerId)}
      />
    </>
  );
}
