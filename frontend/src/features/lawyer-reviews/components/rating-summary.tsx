"use client";

import { Info } from "lucide-react";

import { RatingStars } from "@/components/common/rating-stars";
import { Card, CardContent } from "@/components/ui/card";
import { formatNumber, formatRating, formatReviewCount } from "@/lib/format";

/**
 * Average rating and review count.
 *
 * Both values come from `LawyerProfileResponse`, where the backend maintains
 * them incrementally as reviews arrive - they are authoritative for the WHOLE
 * review history, not just the page on screen.
 *
 * There is deliberately NO star-by-star breakdown. Rendering one needs per-star
 * counts across every review, and no endpoint exposes them: the only readable
 * route is `GET /api/lawyers/{id}/reviews`, which is paginated
 * (`Page<ReviewResponse>`). Counting the ten reviews currently loaded and
 * presenting that as the distribution would be a statistic derived from a
 * partial dataset. The note below tells the lawyer that plainly rather than
 * leaving a conspicuous gap.
 */
export function RatingSummary({
  averageRating,
  totalReviews,
}: {
  averageRating: number;
  totalReviews: number;
}) {
  const hasReviews = totalReviews > 0;

  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex flex-wrap items-center gap-x-8 gap-y-4">
          <div>
            <p className="text-sm text-muted-foreground">Average rating</p>

            {hasReviews ? (
              <p className="mt-1 flex items-baseline gap-2">
                <span className="text-3xl font-semibold tabular-nums">
                  {formatRating(averageRating)}
                </span>
                <RatingStars
                  rating={averageRating}
                  size="sm"
                  showValue={false}
                />
              </p>
            ) : (
              <p className="mt-1 text-3xl font-semibold text-muted-foreground">
                —
              </p>
            )}
          </div>

          <div>
            <p className="text-sm text-muted-foreground">Total reviews</p>
            <p className="mt-1 text-3xl font-semibold tabular-nums">
              {formatNumber(totalReviews)}
            </p>
          </div>
        </div>

        {hasReviews ? (
          <p className="mt-4 inline-flex items-start gap-2 border-t border-border pt-4 text-xs text-muted-foreground">
            <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            <span>
              Based on {formatReviewCount(totalReviews)}. A star-by-star
              breakdown is not shown because reviews are served one page at a
              time, and counting only the loaded page would misstate the
              distribution.
            </span>
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
