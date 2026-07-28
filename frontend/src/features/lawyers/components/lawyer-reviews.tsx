"use client";

import { MessageSquareText } from "lucide-react";
import { useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { Pagination } from "@/components/common/pagination";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useLawyerReviews } from "@/features/lawyers/hooks/use-lawyer-reviews";
import { formatReviewCount } from "@/lib/format";
import { cn } from "@/lib/utils";

import { ReviewCard } from "./review-card";

/**
 * Paginated reviews.
 *
 * Page state is local rather than in the URL: reviews are one section of a
 * page, and putting their page index in the query string would compete with
 * the profile's own address and make sharing a link ambiguous.
 *
 * As elsewhere, `keepPreviousData` keeps the current page mounted while the
 * next loads, so the section does not collapse mid-scroll.
 */
export function LawyerReviews({ lawyerId }: { lawyerId: string }) {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error, refetch, isPlaceholderData } =
    useLawyerReviews(lawyerId, page);

  const reviews = data?.content ?? [];
  const pageMeta = data?.page;
  const total = pageMeta?.totalElements ?? 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Reviews</CardTitle>
        <CardDescription>
          {isPending ? "Loading reviews..." : formatReviewCount(total)}
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        {isPending ? (
          <div className="space-y-4">
            {Array.from({ length: 3 }, (_, index) => (
              <div key={index} className="flex gap-3">
                <Skeleton className="size-8 shrink-0 rounded-full" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-3.5 w-1/3" />
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-4/5" />
                </div>
              </div>
            ))}
          </div>
        ) : isError ? (
          <ErrorState
            error={error}
            onRetry={() => void refetch()}
            title="Could not load reviews"
          />
        ) : reviews.length === 0 ? (
          <EmptyState
            icon={MessageSquareText}
            title="No reviews yet"
            description="Reviews appear here once clients complete a consultation."
          />
        ) : (
          <>
            <div
              className={cn(
                "divide-y divide-border transition-opacity",
                isPlaceholderData && "opacity-60",
              )}
              aria-busy={isPlaceholderData}
            >
              {reviews.map((review) => (
                <ReviewCard key={review.id} review={review} />
              ))}
            </div>

            <Pagination
              page={pageMeta?.number ?? 0}
              totalPages={pageMeta?.totalPages ?? 1}
              onPageChange={setPage}
            />
          </>
        )}
      </CardContent>
    </Card>
  );
}
