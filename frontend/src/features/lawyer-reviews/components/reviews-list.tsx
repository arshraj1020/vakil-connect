"use client";

import type { AppointmentResponse, ReviewResponse } from "@/types";

import { findReviewAppointment } from "../lib/review-utils";
import { ReviewCard } from "./review-card";

/**
 * The page of reviews.
 *
 * A semantic list, so assistive technology announces the count and position.
 * Order is exactly as received - the backend sorts by `createdAt DESC` and this
 * component must not second-guess the page it was given.
 */
export function ReviewsList({
  reviews,
  appointmentsById,
  onSelect,
}: {
  reviews: ReviewResponse[];
  appointmentsById: Map<string, AppointmentResponse>;
  onSelect: (review: ReviewResponse) => void;
}) {
  return (
    <ul className="space-y-3" aria-label="Client reviews">
      {reviews.map((review) => (
        <li key={review.id}>
          <ReviewCard
            review={review}
            appointment={findReviewAppointment(review, appointmentsById)}
            onSelect={onSelect}
          />
        </li>
      ))}
    </ul>
  );
}
