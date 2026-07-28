"use client";

import { ArrowRight } from "lucide-react";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { RatingStars } from "@/components/common/rating-stars";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatTimestamp } from "@/lib/date";
import { hasComment } from "@/lib/reviews";
import type { AdminReviewResponse } from "@/types";

import { isCommentClamped } from "../lib/moderation-utils";
import { ReviewActions } from "./review-actions";

/**
 * One review in the moderation list.
 *
 * Shows exactly the five fields the DTO supports: client name, lawyer name,
 * rating, comment and created date. `appointmentId` is also on the DTO but is
 * not rendered - it is a bare UUID, and an admin cannot resolve it to anything:
 * the appointment endpoints live under `/api/client/**` and `/api/lawyer/**`,
 * which SecurityConfig restricts to those roles, so an admin receives 403.
 * Printing an unresolvable identifier would be noise.
 *
 * Both participants are named because `AdminReviewResponse` carries both - this
 * is the one place in the product showing a review from outside either party's
 * perspective, and moderating "is this review fair?" needs to know who wrote it
 * about whom.
 *
 * A plain region with explicit controls rather than one clickable surface: the
 * row carries both "read full review" and a destructive action, and nesting a
 * button inside a button is invalid HTML with broken keyboard behaviour.
 */
export function ReviewRow({
  review,
  onViewDetails,
}: {
  review: AdminReviewResponse;
  onViewDetails: (review: AdminReviewResponse) => void;
}) {
  const written = hasComment(review);
  const clamped = isCommentClamped(review);

  return (
    <Card>
      <CardContent className="space-y-3 p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            <InitialsAvatar name={review.clientName} size="sm" />

            <div className="min-w-0 space-y-0.5">
              <h3 className="truncate text-sm font-medium">
                {review.clientName}
              </h3>

              <p className="flex flex-wrap items-center gap-1 truncate text-xs text-muted-foreground">
                reviewed
                <ArrowRight className="size-3 shrink-0" aria-hidden />
                <span className="font-medium text-foreground">
                  {review.lawyerName}
                </span>
              </p>

              <p className="text-xs text-muted-foreground">
                {formatTimestamp(review.createdAt)}
              </p>
            </div>
          </div>

          <RatingStars rating={review.rating} size="sm" />
        </div>

        <p
          className={
            written
              ? "line-clamp-3 text-sm leading-relaxed text-muted-foreground"
              : "text-sm italic text-muted-foreground"
          }
        >
          {written ? review.comment : "No written feedback"}
        </p>

        <div className="flex flex-wrap items-center justify-end gap-2">
          {clamped ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onViewDetails(review)}
              aria-label={`Read the full review of ${review.lawyerName} by ${review.clientName}`}
            >
              Read full review
            </Button>
          ) : null}

          <ReviewActions review={review} />
        </div>
      </CardContent>
    </Card>
  );
}
