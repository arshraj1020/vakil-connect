"use client";

import { CalendarDays } from "lucide-react";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { RatingStars } from "@/components/common/rating-stars";
import { Card, CardContent } from "@/components/ui/card";
import { formatDate, formatTimestamp } from "@/lib/date";
import type { AppointmentResponse, ReviewResponse } from "@/types";

import { describeRating, hasComment } from "../lib/review-utils";

/**
 * One review, as a row in the list.
 *
 * The whole card is a <button>, not a div with a click handler and a tabIndex:
 * that gives keyboard activation with both Enter and Space, focus-visible
 * styling and the correct role for free, and screen readers announce it as an
 * actionable control rather than a block of text.
 *
 * `aria-label` names the button by reviewer, rating and date, so the
 * announcement is meaningful out of context. The visible star row is hidden
 * from assistive tech inside it to avoid saying the rating twice.
 *
 * The comment is clamped to three lines; the dialog shows it whole. `comment`
 * is nullable in the DTO - a rating with no words is valid - so its absence is
 * stated rather than left blank.
 */
export function ReviewCard({
  review,
  appointment,
  onSelect,
}: {
  review: ReviewResponse;
  /** Resolved locally by appointmentId; null when unavailable. */
  appointment: AppointmentResponse | null;
  onSelect: (review: ReviewResponse) => void;
}) {
  const written = hasComment(review);

  const label = `Review by ${review.clientName}, ${describeRating(
    review.rating,
  )}, ${formatTimestamp(review.createdAt)}`;

  return (
    <Card className="transition-shadow focus-within:shadow-md hover:shadow-md">
      <CardContent className="p-0">
        <button
          type="button"
          onClick={() => onSelect(review)}
          aria-label={label}
          className="flex w-full items-start gap-3 rounded-xl p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        >
          <InitialsAvatar name={review.clientName} size="sm" />

          <div className="min-w-0 flex-1 space-y-1.5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="truncate text-sm font-medium">{review.clientName}</h3>

              <span aria-hidden>
                <RatingStars rating={review.rating} size="sm" />
              </span>
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

            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
              <span>Reviewed {formatTimestamp(review.createdAt)}</span>

              {appointment ? (
                <span className="inline-flex items-center gap-1">
                  <CalendarDays className="size-3" aria-hidden />
                  Consultation {formatDate(appointment.appointmentDate)}
                </span>
              ) : null}
            </p>
          </div>
        </button>
      </CardContent>
    </Card>
  );
}
