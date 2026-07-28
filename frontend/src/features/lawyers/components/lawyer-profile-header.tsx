"use client";

import { BadgeCheck, CalendarPlus, MapPin, ShieldAlert } from "lucide-react";
import Link from "next/link";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { RatingStars } from "@/components/common/rating-stars";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useAuth } from "@/features/auth/hooks/use-auth";
import { REDIRECT_PARAM } from "@/lib/constants";
import { formatCurrency, formatExperience, formatReviewCount } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { LawyerProfileResponse } from "@/types";

/**
 * Identity, credibility signals and the booking call to action.
 *
 * The CTA mirrors the backend's booking preconditions so a user is never sent
 * into a request that is certain to fail:
 *   - booking is CLIENT-only (a lawyer or admin token receives 403),
 *   - the lawyer must be admin-verified (otherwise 409),
 *   - the lawyer must have published availability (every date would be 409).
 *
 * Anonymous visitors are sent to sign in with a `next` parameter, so they
 * return here rather than landing on a dashboard.
 *
 * Contact details are deliberately not rendered. The endpoint is public and
 * does return `email` and `phoneNumber`, but publishing personal contact
 * information to anonymous visitors is not something the UI should do.
 */
export function LawyerProfileHeader({
  lawyer,
  isBookable,
  isAvailabilityLoading,
}: {
  lawyer: LawyerProfileResponse;
  isBookable: boolean;
  isAvailabilityLoading: boolean;
}) {
  const { isAuthenticated, role } = useAuth();

  const bookingHref = ROUTES.bookAppointment(lawyer.id);
  const isClient = role === "CLIENT";
  const hasReviews = lawyer.totalReviews > 0;

  const bookingBlockedReason = !lawyer.verified
    ? "This lawyer is pending verification and cannot accept bookings yet."
    : !isAvailabilityLoading && !isBookable
      ? "This lawyer has not published any consultation hours yet."
      : isAuthenticated && !isClient
        ? "Only client accounts can book consultations."
        : null;

  const canBook = bookingBlockedReason === null && !isAvailabilityLoading;

  return (
    <Card>
      <CardContent className="flex flex-col gap-6 p-6 sm:flex-row sm:items-start">
        <InitialsAvatar name={lawyer.fullName} size="lg" />

        <div className="min-w-0 flex-1 space-y-3">
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight">
                {lawyer.fullName}
              </h1>

              {lawyer.verified ? (
                <Badge variant="success">
                  <BadgeCheck aria-hidden />
                  Verified
                </Badge>
              ) : (
                <Badge variant="warning">
                  <ShieldAlert aria-hidden />
                  Pending verification
                </Badge>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-4" aria-hidden />
                {lawyer.city}
              </span>
              <span>{formatExperience(lawyer.experienceYears)} of experience</span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {hasReviews ? (
              <>
                <RatingStars rating={lawyer.rating} />
                <span className="text-sm text-muted-foreground">
                  {formatReviewCount(lawyer.totalReviews)}
                </span>
              </>
            ) : (
              <span className="text-sm text-muted-foreground">
                {formatReviewCount(0)}
              </span>
            )}
          </div>
        </div>

        {/* Fee + CTA */}
        <div className="w-full shrink-0 space-y-3 sm:w-56">
          <div className="rounded-xl bg-muted/60 p-4 text-center">
            <p className="text-2xl font-semibold tracking-tight">
              {formatCurrency(lawyer.consultationFee)}
            </p>
            <p className="text-xs text-muted-foreground">per consultation</p>
          </div>

          {canBook ? (
            <Button asChild className="w-full">
              <Link
                href={
                  isAuthenticated
                    ? bookingHref
                    : `${ROUTES.LOGIN}?${REDIRECT_PARAM}=${encodeURIComponent(bookingHref)}`
                }
              >
                <CalendarPlus aria-hidden />
                {isAuthenticated ? "Book consultation" : "Sign in to book"}
              </Link>
            </Button>
          ) : (
            <>
              <Button className="w-full" disabled>
                <CalendarPlus aria-hidden />
                Book consultation
              </Button>
              {bookingBlockedReason ? (
                <p className="text-center text-xs text-muted-foreground">
                  {bookingBlockedReason}
                </p>
              ) : null}
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
