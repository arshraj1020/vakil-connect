"use client";

import { ArrowLeft, UserX } from "lucide-react";
import Link from "next/link";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { Button } from "@/components/ui/button";
import { useLawyerAvailability } from "@/features/lawyers/hooks/use-lawyer-availability";
import { useLawyerProfile } from "@/features/lawyers/hooks/use-lawyer-profile";
import { ROUTES } from "@/lib/routes";
import { isApiError } from "@/types";

import { LawyerAbout } from "./lawyer-about";
import { LawyerAvailability } from "./lawyer-availability";
import { LawyerCredentials } from "./lawyer-credentials";
import { LawyerProfileHeader } from "./lawyer-profile-header";
import { LawyerProfileSkeleton } from "./lawyer-profile-skeleton";
import { LawyerReviews } from "./lawyer-reviews";

/**
 * Lawyer profile.
 *
 * Availability is fetched here (and only here) because the CTA depends on it:
 * a lawyer with no published hours cannot be booked, and the backend would
 * reject every attempt with 409. Loading it alongside the profile lets the
 * button reflect that before the user acts.
 *
 * A 404 is treated as a distinct outcome rather than an error - an unknown id
 * is a normal consequence of a stale link, not a failure to recover from.
 */
export function LawyerProfileView({ lawyerId }: { lawyerId: string }) {
  const { data: lawyer, isPending, isError, error, refetch } =
    useLawyerProfile(lawyerId);

  const { isBookable, isPending: isAvailabilityLoading } =
    useLawyerAvailability(lawyerId);

  if (isPending) {
    return (
      <div className="container py-8">
        <LawyerProfileSkeleton />
      </div>
    );
  }

  if (isError) {
    const notFound = isApiError(error) && error.isNotFound;

    return (
      <div className="container py-8">
        {notFound ? (
          <EmptyState
            icon={UserX}
            title="Lawyer not found"
            description="This profile may have been removed, or the link may be incorrect."
            action={
              <Button asChild variant="outline" size="sm">
                <Link href={ROUTES.LAWYERS}>
                  <ArrowLeft aria-hidden />
                  Back to search
                </Link>
              </Button>
            }
          />
        ) : (
          <ErrorState
            error={error}
            onRetry={() => void refetch()}
            title="Could not load this profile"
          />
        )}
      </div>
    );
  }

  return (
    <div className="container space-y-6 py-8">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={ROUTES.LAWYERS}>
          <ArrowLeft aria-hidden />
          Back to search
        </Link>
      </Button>

      <LawyerProfileHeader
        lawyer={lawyer}
        isBookable={isBookable}
        isAvailabilityLoading={isAvailabilityLoading}
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <LawyerAbout lawyer={lawyer} />
          <LawyerReviews lawyerId={lawyerId} />
        </div>

        <div className="space-y-6">
          {/* Sticky on desktop: keeps hours and credentials visible while
              reading through a long list of reviews. */}
          <div className="space-y-6 lg:sticky lg:top-24">
            <LawyerAvailability lawyerId={lawyerId} />
            <LawyerCredentials lawyer={lawyer} />
          </div>
        </div>
      </div>
    </div>
  );
}
