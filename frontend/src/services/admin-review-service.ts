import api from "@/lib/axios";
import type { AdminReviewResponse, Paged, PageParams } from "@/types";

/**
 * Review moderation. Exactly two admin endpoints exist.
 *
 * What the API does NOT provide, and therefore what this module cannot offer:
 *   - no search, and no filter by rating, lawyer, client or date
 *   - no ordering, and no sort parameter
 *   - no reported/flagged state, and no moderation queue - the list is EVERY
 *     review on the platform, not a set awaiting attention
 *   - no hide, restore, reply or edit
 *   - no soft delete: removal is permanent
 *   - no bulk deletion
 */

const ENDPOINTS = {
  reviews: "/api/admin/reviews",
  review: (reviewId: string) => `/api/admin/reviews/${reviewId}`,
} as const;

/**
 * One page of every review on the platform.
 *
 * ORDERING: none. `reviewRepository.findAll(pageable)` receives
 * `PageRequest.of(page, size)` with no Sort, so no ORDER BY is emitted and row
 * order is whatever Postgres returns. Results must never be labelled newest,
 * oldest or highest rated.
 *
 * Note this differs from the LAWYER-facing listing, which does order by
 * `createdAt DESC` through `findByLawyerOrderByCreatedAtDesc`. The admin view
 * has no such guarantee, so the two screens cannot make the same claim.
 */
export async function getReviews(
  params: PageParams,
): Promise<Paged<AdminReviewResponse>> {
  const { data } = await api.get<Paged<AdminReviewResponse>>(
    ENDPOINTS.reviews,
    { params },
  );
  return data;
}

/**
 * Permanently removes a review. Responds 204 with no body.
 *
 * A HARD delete - `reviewRepository.delete(review)` - so there is no restore
 * endpoint and no soft-deleted state to recover from.
 *
 * It is not only a delete. Inside the same @Transactional method the service
 * repairs the lawyer's aggregate before removing the row: it subtracts this
 * rating from the running mean, decrements `totalReviews`, and resets cleanly
 * to 0.0 / 0 when the last review goes. So a deletion changes the lawyer's
 * PUBLIC rating as well as the review list, which is why the mutation
 * invalidates more than the review cache.
 *
 * Returns 404 for an unknown reviewId.
 */
export async function deleteReview(reviewId: string): Promise<void> {
  await api.delete(ENDPOINTS.review(reviewId));
}

export const adminReviewService = {
  getReviews,
  deleteReview,
} as const;
