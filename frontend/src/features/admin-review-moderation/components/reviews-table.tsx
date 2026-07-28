"use client";

import type { AdminReviewResponse } from "@/types";

import { ReviewRow } from "./review-row";

/**
 * The page of reviews.
 *
 * A semantic <ul> rather than a <table>: each row carries a free-text comment of
 * up to 2000 characters plus controls, which a real table would truncate or
 * force to scroll horizontally, and claiming grid semantics would oblige full
 * keyboard grid navigation to be correct.
 *
 * Rendered in the order received. `reviewRepository.findAll(pageable)` emits no
 * ORDER BY and the controller passes no Sort, so there is no ordering contract
 * to honour and none is implied.
 */
export function ReviewsTable({
  reviews,
  onViewDetails,
}: {
  reviews: AdminReviewResponse[];
  onViewDetails: (review: AdminReviewResponse) => void;
}) {
  return (
    <ul className="space-y-3" aria-label="Reviews">
      {reviews.map((review) => (
        <li key={review.id}>
          <ReviewRow review={review} onViewDetails={onViewDetails} />
        </li>
      ))}
    </ul>
  );
}
