"use client";

import { MessageSquareText, UserX } from "lucide-react";
import { useMemo, useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Pagination } from "@/components/common/pagination";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { ReviewResponse } from "@/types";

import { useLawyerReviews } from "../hooks/use-lawyer-reviews";
import { findReviewAppointment } from "../lib/review-utils";
import { RatingSummary } from "./rating-summary";
import { ReviewDetailsDialog } from "./review-details-dialog";
import { ReviewsList } from "./reviews-list";

/** Matches the backend's own default page size for this endpoint. */
const PAGE_SIZE = 10;

/**
 * The lawyer's reviews.
 *
 * Page state is local rather than in the URL, unlike lawyer search: there is
 * nothing here worth linking to or restoring, and every entry point is the nav
 * item. Changing pages refetches; `keepPreviousData` in the hook keeps the
 * current page visible meanwhile, and the list is dimmed to show it is stale.
 *
 * There is no filter control, because no filter parameter exists on
 * `GET /api/lawyers/{id}/reviews`. Filtering the loaded page client-side would
 * hide matches on every other page, so the only empty state that can occur is
 * "no reviews yet" - a "nothing matches your filter" state would be unreachable
 * and is therefore not built.
 */
export function LawyerReviewsView() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<ReviewResponse | null>(null);

  const params = useMemo(() => ({ page, size: PAGE_SIZE }), [page]);

  const {
    reviews,
    appointmentsById,
    averageRating,
    totalReviews,
    totalPages,
    totalElements,
    isPending,
    isFetching,
    isError,
    error,
    isMissingProfile,
    refetch,
  } = useLawyerReviews(params);

  /* Resolved once for the dialog, so opening it does not re-scan the index. */
  const selectedAppointment = useMemo(
    () => (selected ? findReviewAppointment(selected, appointmentsById) : null),
    [selected, appointmentsById],
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reviews"
        description="What clients have said after their consultations with you."
      />

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={4} />
          </CardContent>
        </Card>
      ) : isMissingProfile ? (
        <Card>
          <CardContent className="p-5">
            <EmptyState
              icon={UserX}
              title="No lawyer profile found"
              description="Reviews are attached to your practice profile, which could not be found for this account. Please contact support."
            />
          </CardContent>
        </Card>
      ) : isError ? (
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={refetch}
              title="Could not load your reviews"
            />
          </CardContent>
        </Card>
      ) : (
        <>
          <RatingSummary
            averageRating={averageRating}
            totalReviews={totalReviews}
          />

          {reviews.length === 0 ? (
            <Card>
              <CardContent className="p-5">
                {/*
                 * Two genuinely different empty results, told apart by the page
                 * metadata rather than by a filter (there is none):
                 *
                 *   totalElements === 0  no client has reviewed this lawyer
                 *   totalElements > 0    this PAGE is empty, which happens when
                 *                        an admin deletes reviews while the
                 *                        lawyer is on a later page
                 */}
                {totalElements === 0 ? (
                  <EmptyState
                    icon={MessageSquareText}
                    title="No reviews yet"
                    description="Clients can leave a review once you mark their consultation as completed."
                  />
                ) : (
                  <EmptyState
                    icon={MessageSquareText}
                    title="Nothing on this page"
                    description="These reviews are no longer here. Go back to the first page to see the current list."
                    action={
                      <Button variant="outline" onClick={() => setPage(0)}>
                        Back to first page
                      </Button>
                    }
                  />
                )}
              </CardContent>
            </Card>
          ) : (
            <div
              className={cn(
                "space-y-4 transition-opacity",
                isFetching && "opacity-60",
              )}
            >
              <ReviewsList
                reviews={reviews}
                appointmentsById={appointmentsById}
                onSelect={setSelected}
              />

              <Pagination
                page={page}
                totalPages={totalPages}
                onPageChange={setPage}
              />
            </div>
          )}
        </>
      )}

      <ReviewDetailsDialog
        review={selected}
        appointment={selectedAppointment}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </div>
  );
}
