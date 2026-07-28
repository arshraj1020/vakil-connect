"use client";

import { Briefcase, MapPin } from "lucide-react";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { RatingStars } from "@/components/common/rating-stars";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCurrency, formatExperience, formatReviewCount } from "@/lib/format";
import type { LawyerProfileFormValues } from "../schemas/lawyer-profile-schema";
import type { LawyerProfileResponse } from "@/types";

/**
 * How the profile will read to a client, updated as the form is edited.
 *
 * Driven by live form values rather than the saved profile, so the effect of a
 * change is visible before committing it. That matters most for the fee and the
 * bio, whose presentation (currency formatting, truncation in search results)
 * is not obvious from the raw input.
 *
 * Fields the lawyer cannot edit - name, rating, review count - come from the
 * saved profile, since no edit can move them.
 *
 * Numbers are parsed defensively: mid-edit the field may hold "" or "12a",
 * which must render as a placeholder rather than "₹NaN".
 */
export function ProfilePreview({
  profile,
  values,
}: {
  profile: LawyerProfileResponse;
  values: LawyerProfileFormValues;
}) {
  const fee = Number(values.consultationFee);
  const years = Number(values.experienceYears);

  const hasFee = values.consultationFee !== "" && !Number.isNaN(fee);
  const hasYears = values.experienceYears !== "" && Number.isInteger(years);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Preview</CardTitle>
        <CardDescription>
          How your profile appears to clients{profile.verified ? "" : " once verified"}.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="flex items-start gap-3">
          <InitialsAvatar name={profile.fullName} />

          <div className="min-w-0 flex-1 space-y-1">
            <p className="truncate font-semibold">{profile.fullName}</p>

            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3" aria-hidden />
                {values.city || "City not set"}
              </span>
              <span className="inline-flex items-center gap-1">
                <Briefcase className="size-3" aria-hidden />
                {hasYears ? formatExperience(years) : "Experience not set"}
              </span>
            </p>

            {profile.totalReviews > 0 ? (
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <RatingStars rating={profile.rating} size="sm" />
                {formatReviewCount(profile.totalReviews)}
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">No reviews yet</p>
            )}
          </div>

          <p className="shrink-0 text-right text-sm font-semibold">
            {hasFee ? formatCurrency(fee) : "—"}
          </p>
        </div>

        {values.specializations.length > 0 ? (
          <ul className="flex flex-wrap gap-1.5">
            {values.specializations.map((specialization) => (
              <li key={specialization}>
                <Badge variant="secondary">{specialization}</Badge>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-muted-foreground">
            No practice areas selected
          </p>
        )}

        <p className="whitespace-pre-line border-t border-border pt-4 text-sm leading-relaxed text-muted-foreground">
          {values.bio || "Your biography will appear here."}
        </p>
      </CardContent>
    </Card>
  );
}
