"use client";

import { Trash2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { Button } from "@/components/ui/button";
import { isApiError, type AdminReviewResponse } from "@/types";

import { useDeleteReview } from "../hooks/use-delete-review";
import { describeReview } from "../lib/moderation-utils";

/**
 * The only moderation action the backend supports.
 *
 * There is no Hide, Restore, Reply, Flag or Edit control, because no such
 * endpoint exists - the admin API offers `GET /api/admin/reviews` and
 * `DELETE /api/admin/reviews/{id}` and nothing else. None is rendered even
 * disabled, since that would advertise a workflow the product does not have.
 *
 * Confirmation states the two things the endpoint actually does, and nothing
 * beyond them: the removal is permanent (a hard delete, with no restore route),
 * and the lawyer's public rating is recomputed in the same transaction. The
 * second is worth saying because it is not implied by the word "delete".
 *
 * Owns its own mutation instance so each row tracks its own pending state.
 */
export function ReviewActions({
  review,
  onDeleted,
  size = "sm",
}: {
  review: AdminReviewResponse;
  /** Lets a parent close its dialog once the review is gone. */
  onDeleted?: () => void;
  size?: "sm" | "default";
}) {
  const [isConfirming, setIsConfirming] = useState(false);

  const mutation = useDeleteReview({
    onSuccess: () => {
      setIsConfirming(false);

      toast.success("Review deleted", {
        description: `${review.lawyerName}'s rating has been recalculated.`,
      });

      onDeleted?.();
    },

    onError: (error) => {
      setIsConfirming(false);

      toast.error("Could not delete this review", {
        description: isApiError(error)
          ? error.status === 404
            ? "This review has already been removed."
            : error.message
          : "Please try again.",
      });
    },
  });

  return (
    <>
      <Button
        variant="outline"
        size={size}
        onClick={() => setIsConfirming(true)}
        disabled={mutation.isPending}
        aria-label={`Delete ${describeReview(review)}`}
        className="text-muted-foreground hover:text-destructive"
      >
        <Trash2 aria-hidden />
        Delete
      </Button>

      <ConfirmDialog
        open={isConfirming}
        onOpenChange={setIsConfirming}
        title="Delete this review?"
        description={`This will permanently remove the review and automatically recalculate ${review.lawyerName}'s public rating. This action cannot be undone.`}
        confirmLabel="Delete permanently"
        destructive
        isPending={mutation.isPending}
        onConfirm={() => mutation.mutate(review.id)}
      />
    </>
  );
}
