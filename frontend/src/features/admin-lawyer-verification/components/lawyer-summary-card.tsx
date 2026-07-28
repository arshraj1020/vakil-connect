"use client";

import { Briefcase, MapPin } from "lucide-react";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { RatingStars } from "@/components/common/rating-stars";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatCurrency, formatExperience } from "@/lib/format";
import type { LawyerSummaryResponse } from "@/types";

import { hasMeaningfulRating } from "../lib/verification-utils";
import { VerificationActions } from "./verification-actions";

/** Practice areas shown before the rest collapse into a "+N" chip. */
const VISIBLE_SPECIALIZATIONS = 3;

/**
 * One pending application.
 *
 * Renders only what `LawyerSummaryResponse` actually carries: name, city,
 * experience, fee, specializations. Email and bar council number are NOT in
 * this DTO, so they appear in the details dialog, which fetches the full
 * profile - they are not invented here.
 *
 * The card is a plain region with two explicit controls rather than one large
 * clickable surface. A row needs both "review" and "verify", and nesting a
 * button inside a button is invalid HTML with genuinely broken keyboard
 * behaviour, so the actions are separate and individually reachable by Tab.
 *
 * No verification-status badge: every lawyer on this screen is unverified by
 * construction (`findByVerifiedFalse`), so a status chip would be a constant
 * and carry no information.
 */
export function LawyerSummaryCard({
  lawyer,
  onReview,
}: {
  lawyer: LawyerSummaryResponse;
  onReview: (lawyer: LawyerSummaryResponse) => void;
}) {
  const visible = lawyer.specializations.slice(0, VISIBLE_SPECIALIZATIONS);
  const overflow = lawyer.specializations.length - visible.length;

  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-start">
        <InitialsAvatar name={lawyer.fullName} />

        <div className="min-w-0 flex-1 space-y-2">
          <div className="space-y-1">
            <h3 className="truncate font-medium">{lawyer.fullName}</h3>

            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3" aria-hidden />
                {lawyer.city}
              </span>
              <span className="inline-flex items-center gap-1">
                <Briefcase className="size-3" aria-hidden />
                {formatExperience(lawyer.experienceYears)}
              </span>
              <span>{formatCurrency(lawyer.consultationFee)} per consultation</span>
            </p>
          </div>

          {visible.length > 0 ? (
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
          ) : (
            <p className="text-xs text-muted-foreground">
              No practice areas listed
            </p>
          )}

          {/*
           * An unverified lawyer cannot be booked, so cannot have completed a
           * consultation, so cannot have been reviewed - rating is structurally
           * 0.0 here. Printing "0.0" would read as a poor score rather than as
           * no data, which could wrongly influence the decision.
           */}
          {hasMeaningfulRating(lawyer) ? (
            <RatingStars rating={lawyer.rating} size="sm" />
          ) : (
            <p className="text-xs text-muted-foreground">
              Not yet rated — no consultations completed
            </p>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => onReview(lawyer)}>
            View details
          </Button>

          <VerificationActions
            lawyerId={lawyer.id}
            lawyerName={lawyer.fullName}
          />
        </div>
      </CardContent>
    </Card>
  );
}
