"use client";

import { CalendarDays, Scale, User } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { RatingStars } from "@/components/common/rating-stars";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatTimestamp } from "@/lib/date";
import { hasComment } from "@/lib/reviews";
import type { AdminReviewResponse } from "@/types";

import { ReviewActions } from "./review-actions";

/**
 * The full review, for moderating a long comment.
 *
 * Opened only when the card clamped the text, so it exists to show the whole
 * comment rather than to reveal extra fields - there are none. Everything the
 * backend holds and this screen renders is already on the card; a comment may
 * simply run to 2000 characters.
 *
 * No fetch. There is no single-review endpoint, and none is needed: the row IS
 * the complete `AdminReviewResponse`.
 *
 * Reuses the shared Dialog, so focus trapping, Escape and the overlay come from
 * Radix rather than being rebuilt.
 */
export function ReviewDetailsDialog({
  review,
  open,
  onOpenChange,
}: {
  review: AdminReviewResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  if (!review) return null;

  const written = hasComment(review);

  const rows: Array<{ icon: LucideIcon; label: string; value: string }> = [
    { icon: User, label: "Client", value: review.clientName },
    { icon: Scale, label: "Lawyer", value: review.lawyerName },
    {
      icon: CalendarDays,
      label: "Written",
      value: formatTimestamp(review.createdAt),
    },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Review</DialogTitle>
          <DialogDescription>
            {review.clientName} on {review.lawyerName}.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-muted-foreground">Rating</span>
            <RatingStars rating={review.rating} />
          </div>

          <dl className="space-y-3 border-t border-border pt-4">
            {rows.map((row) => {
              const Icon = row.icon;

              return (
                <div
                  key={row.label}
                  className="flex items-start justify-between gap-4"
                >
                  <dt className="inline-flex shrink-0 items-center gap-2 text-sm text-muted-foreground">
                    <Icon className="size-4" aria-hidden />
                    {row.label}
                  </dt>
                  <dd className="break-words text-right text-sm font-medium">
                    {row.value}
                  </dd>
                </div>
              );
            })}
          </dl>

          <div className="space-y-2 border-t border-border pt-4">
            <h4 className="text-sm text-muted-foreground">Feedback</h4>
            <p
              className={
                written
                  ? "whitespace-pre-line text-sm leading-relaxed"
                  : "text-sm italic text-muted-foreground"
              }
            >
              {written
                ? review.comment
                : "This client rated the consultation without writing a comment."}
            </p>
          </div>
        </div>

        <DialogFooter className="mt-2">
          <ReviewActions
            review={review}
            size="default"
            // The review no longer exists, so the dialog is showing a
            // record that has been removed.
            onDeleted={() => onOpenChange(false)}
          />
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
