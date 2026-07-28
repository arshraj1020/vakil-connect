"use client";

import { BadgeCheck, ShieldAlert, Star } from "lucide-react";
import Link from "next/link";

import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useAuth } from "@/features/auth/hooks/use-auth";
import { useLawyerDashboard } from "@/features/lawyer-dashboard/hooks/use-lawyer-dashboard";
import { formatRating, formatReviewCount } from "@/lib/format";
import { ROUTES } from "@/lib/routes";

/**
 * Greeting, verification state and review standing.
 *
 * Verification is surfaced prominently because it gates the lawyer's entire
 * funnel: unverified profiles are excluded from public search and the backend
 * rejects any booking against them with 409. A lawyer wondering why they have
 * no appointments needs that answer immediately, not buried in a profile page.
 */
export function WelcomeHeader() {
  const { user } = useAuth();
  const { data, isPending } = useLawyerDashboard();

  const firstName = user?.fullName.split(" ")[0] ?? "";

  return (
    <div className="space-y-4">
      <PageHeader
        title={firstName ? `Welcome back, ${firstName}` : "Welcome back"}
        description="Your practice at a glance."
        actions={
          !isPending && data ? (
            <div className="flex items-center gap-2">
              {data.profileVerified ? (
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

              {data.totalReviews > 0 ? (
                <Badge variant="secondary">
                  <Star aria-hidden />
                  {formatRating(data.averageRating)} ·{" "}
                  {formatReviewCount(data.totalReviews)}
                </Badge>
              ) : null}
            </div>
          ) : null
        }
      />

      {!isPending && data && !data.profileVerified ? (
        <Card className="border-warning/40 bg-warning/5">
          <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="space-y-0.5">
              <p className="text-sm font-medium">
                Your profile is awaiting verification
              </p>
              <p className="text-sm text-muted-foreground">
                Until an administrator verifies you, your profile stays hidden
                from search and clients cannot book consultations.
              </p>
            </div>

            <Button asChild variant="outline" size="sm" className="shrink-0">
              <Link href={ROUTES.LAWYER_PROFILE}>Review profile</Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
