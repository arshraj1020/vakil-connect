import { Briefcase, MapPin, Star } from "lucide-react";
import Link from "next/link";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  formatCurrency,
  formatExperience,
  formatRating,
  formatReviewCount,
} from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import type { LawyerSummaryResponse } from "@/types";

/**
 * A lawyer in search results.
 *
 * Renders only what `LawyerSummaryResponse` carries. Availability is
 * deliberately absent: it lives on a separate endpoint, so showing it here
 * would mean one extra request per card - ten round-trips for a page of
 * results. It appears on the profile, where a single request is justified.
 *
 * The whole card is a link, so the hit target is the card rather than just the
 * name.
 */
export function LawyerCard({
  lawyer,
  className,
}: {
  lawyer: LawyerSummaryResponse;
  className?: string;
}) {
  const hasReviews = lawyer.totalReviews > 0;
  const visibleSpecializations = lawyer.specializations.slice(0, 3);
  const remaining = lawyer.specializations.length - visibleSpecializations.length;

  return (
    <Link
      href={ROUTES.lawyerDetail(lawyer.id)}
      className={cn(
        "block rounded-xl",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
        className,
      )}
    >
      <Card className="h-full transition-shadow hover:shadow-md">
        <CardContent className="flex h-full flex-col gap-4 p-5">
          <div className="flex items-start gap-3">
            <InitialsAvatar name={lawyer.fullName} />

            <div className="min-w-0 flex-1 space-y-1">
              <p className="truncate font-semibold">{lawyer.fullName}</p>

              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <MapPin className="size-3" aria-hidden />
                  {lawyer.city}
                </span>
                <span className="inline-flex items-center gap-1">
                  <Briefcase className="size-3" aria-hidden />
                  {formatExperience(lawyer.experienceYears)}
                </span>
              </div>
            </div>
          </div>

          {/* Rating */}
          <div className="flex items-center gap-1.5 text-sm">
            {hasReviews ? (
              <>
                <Star
                  className="size-4 fill-warning text-warning"
                  aria-hidden
                />
                <span className="font-medium">{formatRating(lawyer.rating)}</span>
                <span className="text-xs text-muted-foreground">
                  ({formatReviewCount(lawyer.totalReviews)})
                </span>
              </>
            ) : (
              <span className="text-xs text-muted-foreground">
                {formatReviewCount(0)}
              </span>
            )}
          </div>

          {visibleSpecializations.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {visibleSpecializations.map((specialization) => (
                <Badge key={specialization} variant="secondary">
                  {specialization}
                </Badge>
              ))}
              {remaining > 0 ? (
                <Badge variant="outline">+{remaining}</Badge>
              ) : null}
            </div>
          ) : null}

          <div className="mt-auto flex items-baseline gap-1 border-t border-border pt-3">
            <span className="text-base font-semibold">
              {formatCurrency(lawyer.consultationFee)}
            </span>
            <span className="text-xs text-muted-foreground">/ consultation</span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
