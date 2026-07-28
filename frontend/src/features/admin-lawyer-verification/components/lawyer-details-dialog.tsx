"use client";

import {
  AtSign,
  BadgeCheck,
  Briefcase,
  MapPin,
  Phone,
  ScrollText,
  TriangleAlert,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatCurrency, formatExperience } from "@/lib/format";
import type { LawyerSummaryResponse } from "@/types";

import { useLawyerForReview } from "../hooks/use-pending-lawyers";
import { awaitsVerification, findMissingDetails } from "../lib/verification-utils";
import { VerificationActions } from "./verification-actions";

/**
 * The full application, for making the verification decision.
 *
 * Fetches `GET /api/lawyers/{id}` on open rather than reusing the row's data.
 * That endpoint is permitAll and resolves through a plain `findById` with no
 * verified predicate, so it returns unverified lawyers - and it is the ONLY
 * source of the fields the decision actually rests on:
 *
 *   barCouncilNumber   the practising credential, absent from the summary DTO
 *   email, phoneNumber contact details, likewise absent
 *   bio, officeAddress the substance of the application
 *
 * Fetched lazily, on open, so opening the screen does not fire one request per
 * row. Cached at `lawyers.detail(id)` - the same entry the public profile page
 * uses, because it is the same resource.
 *
 * Reuses the shared Dialog, so focus trapping, Escape and the overlay come from
 * Radix rather than being rebuilt.
 */
export function LawyerDetailsDialog({
  lawyer,
  open,
  onOpenChange,
}: {
  /** The queue row that was opened; null when closed. */
  lawyer: LawyerSummaryResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: profile, isPending, isError, error, refetch } =
    useLawyerForReview(lawyer?.id ?? null);

  if (!lawyer) return null;

  const missing = profile ? findMissingDetails(profile) : [];

  const contactRows: Array<{ icon: LucideIcon; label: string; value: string }> =
    profile
      ? [
          {
            icon: ScrollText,
            label: "Bar council number",
            value: profile.barCouncilNumber || "Not provided",
          },
          { icon: AtSign, label: "Email", value: profile.email },
          {
            icon: Phone,
            label: "Phone",
            value: profile.phoneNumber ?? "Not provided",
          },
          {
            icon: Briefcase,
            label: "Experience",
            value: formatExperience(profile.experienceYears),
          },
          {
            icon: MapPin,
            label: "Office",
            value: `${profile.officeAddress}, ${profile.city}`,
          },
        ]
      : [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{lawyer.fullName}</DialogTitle>
          <DialogDescription>
            Review this application before verifying.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          {isPending ? (
            <ListSkeleton count={4} />
          ) : isError || !profile ? (
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load this application"
            />
          ) : (
            <>
              {missing.length > 0 ? (
                <p className="flex items-start gap-2 rounded-lg bg-warning/10 p-3 text-xs text-warning">
                  <TriangleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                  <span>
                    Incomplete application. Missing: {missing.join(", ")}.
                  </span>
                </p>
              ) : null}

              <dl className="space-y-3">
                {contactRows.map((row) => {
                  const Icon = row.icon;

                  return (
                    <div
                      key={row.label}
                      className="flex items-start justify-between gap-4"
                    >
                      <dt className="inline-flex shrink-0 items-center gap-2 text-sm text-muted-foreground">
                        <Icon className="size-4" aria-hidden />
                        {row.label}
                      </dt>
                      <dd className="break-words text-right text-sm font-medium">
                        {row.value}
                      </dd>
                    </div>
                  );
                })}

                <div className="flex items-start justify-between gap-4">
                  <dt className="text-sm text-muted-foreground">
                    Consultation fee
                  </dt>
                  <dd className="text-right text-sm font-medium">
                    {formatCurrency(profile.consultationFee)}
                  </dd>
                </div>
              </dl>

              <div className="space-y-2 border-t border-border pt-4">
                <h4 className="text-sm text-muted-foreground">Practice areas</h4>

                {profile.specializations.length > 0 ? (
                  <ul className="flex flex-wrap gap-1.5">
                    {profile.specializations.map((specialization) => (
                      <li key={specialization}>
                        <Badge variant="secondary">{specialization}</Badge>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm italic text-muted-foreground">
                    None listed
                  </p>
                )}
              </div>

              <div className="space-y-2 border-t border-border pt-4">
                <h4 className="text-sm text-muted-foreground">Biography</h4>
                <p className="whitespace-pre-line text-sm leading-relaxed">
                  {profile.bio || "No biography provided."}
                </p>
              </div>
            </>
          )}
        </div>

        {profile ? (
          <DialogFooter className="mt-2">
            {awaitsVerification(profile) ? (
              <VerificationActions
                lawyerId={profile.id}
                lawyerName={profile.fullName}
                // The lawyer has left the queue, so the dialog behind it is
                // showing a row that no longer exists.
                onVerified={() => onOpenChange(false)}
              />
            ) : (
              <Badge variant="success" className="gap-1.5">
                <BadgeCheck className="size-3.5" aria-hidden />
                Verified
              </Badge>
            )}
          </DialogFooter>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
