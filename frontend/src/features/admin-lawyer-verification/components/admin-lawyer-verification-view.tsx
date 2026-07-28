"use client";

import { CheckCircle2 } from "lucide-react";
import { useMemo, useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { PaginationControls } from "@/components/common/pagination-controls";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { LawyerSummaryResponse } from "@/types";

import { usePendingLawyers } from "../hooks/use-pending-lawyers";
import { LawyerDetailsDialog } from "./lawyer-details-dialog";
import { PendingLawyersTable } from "./pending-lawyers-table";

/** Matches the endpoint's own `defaultValue = "10"`. */
const PAGE_SIZE = 10;

/**
 * Lawyer verification queue.
 *
 * There is no search box and no filter control, because
 * `GET /api/admin/lawyers/pending` accepts neither. Filtering the loaded page
 * client-side would hide matches on every other page, so the only empty states
 * that can occur are "queue is clear" and "this page is empty" - the latter
 * happening when verifications elsewhere shrink the queue while an admin sits
 * on a later page.
 *
 * Only verification is offered. No reject or un-verify endpoint exists, so no
 * such control is rendered, not even disabled - see VerificationActions.
 *
 * 401 and 403 do not reach this component: the Axios interceptor handles 401 by
 * clearing the session and redirecting, and RoleGuard in the /admin layout
 * catches a non-admin before these children mount. ErrorState therefore handles
 * network failure and 5xx, which it already distinguishes.
 */
export function AdminLawyerVerificationView() {
  const [page, setPage] = useState(0);
  const [reviewing, setReviewing] = useState<LawyerSummaryResponse | null>(null);

  const params = useMemo(() => ({ page, size: PAGE_SIZE }), [page]);

  const {
    lawyers,
    totalPages,
    totalElements,
    isPending,
    isFetching,
    isError,
    error,
    refetch,
  } = usePendingLawyers(params);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Lawyer verification"
        description="Review applications and verify lawyers so they appear in client search."
      />

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={4} />
          </CardContent>
        </Card>
      ) : isError ? (
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load the verification queue"
            />
          </CardContent>
        </Card>
      ) : lawyers.length === 0 ? (
        <Card>
          <CardContent className="p-5">
            {totalElements === 0 ? (
              <EmptyState
                icon={CheckCircle2}
                title="Queue is clear"
                description="Every lawyer profile has been verified. New applications will appear here as lawyers register."
              />
            ) : (
              <EmptyState
                icon={CheckCircle2}
                title="Nothing on this page"
                description="These applications have already been handled. Go back to the first page to see the rest of the queue."
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
          <PendingLawyersTable lawyers={lawyers} onReview={setReviewing} />

          <PaginationControls
            page={page}
            size={PAGE_SIZE}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={setPage}
            itemLabel="awaiting verification"
          />
        </div>
      )}

      <LawyerDetailsDialog
        lawyer={reviewing}
        open={reviewing !== null}
        onOpenChange={(open) => {
          if (!open) setReviewing(null);
        }}
      />
    </div>
  );
}
