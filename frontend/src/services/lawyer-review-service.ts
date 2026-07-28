import { getLawyerReviews } from "@/services/lawyer-service";
import type { Paged, PageParams, ReviewResponse } from "@/types";

/**
 * Reviews as the lawyer portal consumes them.
 *
 * There is NO lawyer-scoped review endpoint. The API exposes exactly three
 * review routes:
 *
 *   POST /api/client/appointments/{id}/review   ROLE_CLIENT, creates one
 *   GET  /api/lawyers/{lawyerId}/reviews        permitAll, paged
 *   GET  /api/admin/reviews                     ROLE_ADMIN
 *
 * So a lawyer reads their own reviews through the PUBLIC endpoint, which needs
 * a lawyerId - obtainable only via GET /api/lawyer/profile.
 *
 * This module deliberately DELEGATES to `lawyer-service` rather than issuing
 * its own request. It is the same URL, the same contract and the same response;
 * a second axios call here would be a duplicate that could drift. What this file
 * adds is the portal's vocabulary ("my reviews") and the documentation of why
 * the public route is the right one to call.
 */

/**
 * One page of reviews for the given lawyer.
 *
 * Ordering is fixed by the backend: the repository method is
 * `findByLawyerOrderByCreatedAtDesc`, and the controller exposes no sort
 * parameter. Newest first, always - callers must not re-sort.
 *
 * `size` defaults to 10 server-side. Returns 404 for an unknown lawyerId.
 */
export function getMyReviews(
  lawyerId: string,
  params: PageParams,
): Promise<Paged<ReviewResponse>> {
  return getLawyerReviews(lawyerId, params);
}

export const lawyerReviewService = {
  getMyReviews,
} as const;
