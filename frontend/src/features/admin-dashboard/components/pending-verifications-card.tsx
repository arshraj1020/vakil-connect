"use client";

import { ArrowRight, CheckCircle2, MapPin } from "lucide-react";
import Link from "next/link";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { InitialsAvatar } from "@/components/common/initials-avatar";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { formatExperience, formatNumber } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { LawyerSummaryResponse } from "@/types";

/** Practice areas shown before collapsing the rest into a "+N" chip. */
const VISIBLE_SPECIALIZATIONS = 2;

/**
 * A preview of the lawyer verification queue.
 *
 * Deliberately NOT described as "newest" or "oldest" applications.
 * `findByVerifiedFalse(pageable)` carries no ordering and the controller
 * accepts no sort parameter, so the five shown are an arbitrary slice of the
 * queue. The card says "5 of 12" and sends the admin to the full screen rather
 * than implying a priority order that the API does not provide.
 *
 * The application date is absent for the same reason it is absent everywhere
 * else: `LawyerSummaryResponse` has no `createdAt` field, unlike
 * `UserSummaryResponse`. Showing a date here would mean inventing one.
 *
 * The count in the header comes from analytics (`unverifiedLawyers`, a COUNT
 * over every unverified lawyer), not from this page's length - the page only
 * ever knows about its own five rows.
 */
export function PendingVerificationsCard({
  lawyers,
  total,
  isLoading,
  isError,
  error,
  onRetry,
}: {
  lawyers: LawyerSummaryResponse[];
  /** Authoritative queue size from analytics. */
  total: number;
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  onRetry: () => void;
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
        <div className="space-y-1.5">
          <CardTitle>Pending verification</CardTitle>
          <CardDescription>
            {total > 0
              ? `${formatNumber(total)} ${
                  total === 1 ? "lawyer is" : "lawyers are"
                } waiting to be verified.`
              : "Every lawyer profile has been reviewed."}
          </CardDescription>
        </div>

        {total > 0 ? (
          <Button variant="outline" size="sm" asChild>
            <Link href={ROUTES.ADMIN_LAWYERS}>
              View all
              <ArrowRight aria-hidden />
            </Link>
          </Button>
        ) : null}
      </CardHeader>

      <CardContent>
        {isLoading ? (
          <ListSkeleton count={3} />
        ) : isError ? (
          <ErrorState
            error={error}
            onRetry={onRetry}
            title="Could not load the verification queue"
          />
        ) : lawyers.length === 0 ? (
          <EmptyState
            icon={CheckCircle2}
            title="Nothing to verify"
            description="New lawyer applications will appear here as they register."
          />
        ) : (
          <>
            <ul className="space-y-3">
              {lawyers.map((lawyer) => {
                const visible = lawyer.specializations.slice(
                  0,
                  VISIBLE_SPECIALIZATIONS,
                );
                const overflow =
                  lawyer.specializations.length - visible.length;

                return (
                  <li
                    key={lawyer.id}
                    className="flex items-start gap-3 rounded-lg bg-muted/50 p-3"
                  >
                    <InitialsAvatar name={lawyer.fullName} size="sm" />

                    <div className="min-w-0 flex-1 space-y-1.5">
                      <p className="truncate text-sm font-medium">
                        {lawyer.fullName}
                      </p>

                      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                        <span className="inline-flex items-center gap-1">
                          <MapPin className="size-3" aria-hidden />
                          {lawyer.city}
                        </span>
                        <span>{formatExperience(lawyer.experienceYears)}</span>
                      </p>

                      {lawyer.specializations.length > 0 ? (
                        <ul className="flex flex-wrap gap-1.5">
                          {visible.map((specialization) => (
                            <li key={specialization}>
                              <Badge variant="secondary">{specialization}</Badge>
                            </li>
                          ))}

                          {overflow > 0 ? (
                            <li>
                              <Badge variant="outline">+{overflow}</Badge>
                            </li>
                          ) : null}
                        </ul>
                      ) : null}
                    </div>
                  </li>
                );
              })}
            </ul>

            {total > lawyers.length ? (
              <p className="mt-3 text-xs text-muted-foreground">
                Showing {lawyers.length} of {formatNumber(total)}. The queue has
                no fixed order — open the verification screen to work through it.
              </p>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}
