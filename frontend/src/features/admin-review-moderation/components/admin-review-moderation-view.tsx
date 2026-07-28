"use client";

import { MessageSquareText } from "lucide-react";
import { useMemo, useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { PaginationControls } from "@/components/common/pagination-controls";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { AdminReviewResponse } from "@/types";

import { useAdminReviews } from "../hooks/use-admin-reviews";
import { ReviewDetailsDialog } from "./review-details-dialog";
import { ReviewsTable } from "./reviews-table";

/** Matches the endpoint's own `defaultValue = "10"`. */
const PAGE_SIZE = 10;

/**
 * Review moderation.
 *
 * This is EVERY review on the platform, not a queue of reported ones. The
 * backend keeps no reported or flagged state, so there is nothing to triage by
 * - `findAll(pageable)` is the whole contract. The page description says so
 * plainly rather than implying a moderation inbox that does not exist.
 *
 * No search box and no filter control: the endpoint accepts neither, and
 * filtering the ten loaded rows while presenting it as a search would hide
 * every match on every other page.
 *
 * Nothing is described as newest, oldest or highest rated. No ORDER BY is
 * emitted and no sort parameter exists, so no ordering contract can be claimed.
 * `createdAt` is shown per row because it is real data, but the list is not
 * asserted to be ordered by it.
 *
 * The details dialog is opened only for long comments - the row already shows
 * every field the DTO carries, so the dialog exists to show the whole of one of
 * them, not to reveal more.
 *
 * 401 and 403 do not reach here: the Axios interceptor handles 401, and
 * RoleGuard in the /admin layout catches non-admins before these children
 * mount. ErrorState covers network failure and 5xx.
 */
export function AdminReviewModerationView() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AdminReviewResponse | null>(null);

  const params = useMemo(() => ({ page, size: PAGE_SIZE }), [page]);

  const {
    reviews,
    totalPages,
    totalElements,
    isPending,
    isFetching,
    isError,
    error,
    refetch,
  } = useAdminReviews(params);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Review moderation"
        description="Every review clients have left. Remove any that breach the guidelines."
      />

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={5} />
          </CardContent>
        </Card>
      ) : isError ? (
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load reviews"
            />
          </CardContent>
        </Card>
      ) : reviews.length === 0 ? (
        <Card>
          <CardContent className="p-5">
            {/*
             * Two empty results, told apart by page metadata rather than by a
             * filter (there is none):
             *
             *   totalElements === 0  no client has reviewed anyone yet
             *   totalElements > 0    this PAGE emptied, which happens after
             *                        deleting the last reviews on a later page
             */}
            {totalElements === 0 ? (
              <EmptyState
                icon={MessageSquareText}
                title="No reviews yet"
                description="Reviews will appear here once clients rate their completed consultations."
              />
            ) : (
              <EmptyState
                icon={MessageSquareText}
                title="Nothing on this page"
                description="These reviews have been removed. Go back to the first page to see the current list."
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
          <ReviewsTable reviews={reviews} onViewDetails={setSelected} />

          <PaginationControls
            page={page}
            size={PAGE_SIZE}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={setPage}
            itemLabel="reviews"
          />
        </div>
      )}

      <ReviewDetailsDialog
        review={selected}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </div>
  );
}
