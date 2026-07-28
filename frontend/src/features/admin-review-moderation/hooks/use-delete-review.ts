"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { adminReviewService } from "@/services/admin-review-service";

/**
 * Permanently deletes one review.
 *
 * No optimistic update. The deletion is irreversible, so removing the row
 * before the server confirms would be a claim the UI could not retract if the
 * request failed - it would have to resurrect a row it had already told the
 * admin was gone.
 *
 * INVALIDATION. `deleteReview` writes to two places in one transaction: it
 * removes the review AND repairs the lawyer's aggregate rating. So three cache
 * groups are genuinely stale, and they are invalidated for stated reasons:
 *
 *   admin.reviews prefix   the review is gone from every cached page
 *
 *   dashboard.admin()      analytics reports `totalReviews` from
 *                          `reviewRepository.count()` and derives
 *                          `averagePlatformRating` from lawyer ratings - the
 *                          deletion moves both
 *
 *   lawyers prefix         the lawyer's `rating` and `totalReviews` changed, so
 *                          their public detail, any search page listing them,
 *                          and their own review list all now disagree with the
 *                          database
 *
 * The lawyers invalidation is deliberately BROAD because it cannot be precise:
 * `AdminReviewResponse` carries `lawyerName` but no `lawyerId`, so there is no
 * way to name the affected lawyer's cache entry. Invalidating the prefix is the
 * narrowest CORRECT option available under this DTO - anything tighter would
 * leave a stale rating on screen. In an admin session these entries are usually
 * empty anyway, so the practical cost is nil.
 *
 * NOT invalidated: users, appointments and availability. A review deletion
 * touches none of them.
 *
 * No bulk variant, because the API has no bulk endpoint.
 */
export function useDeleteReview(options?: {
  onSuccess?: () => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (reviewId: string) => adminReviewService.deleteReview(reviewId),

    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: [...queryKeys.admin.all, "reviews"],
      });

      void queryClient.invalidateQueries({
        queryKey: queryKeys.dashboard.admin(),
      });

      void queryClient.invalidateQueries({
        queryKey: queryKeys.lawyers.all,
      });

      options?.onSuccess?.();
    },

    onError: (error) => options?.onError?.(error),
  });
}
