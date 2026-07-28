"use client";

import { UserX } from "lucide-react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

import { useMyProfile } from "../hooks/use-my-profile";
import { ContactSection } from "./contact-section";
import { ProfileForm } from "./profile-form";

/**
 * Profile screen for the signed-in lawyer.
 *
 * Resolves the four load states here so `ProfileForm` can take a guaranteed
 * profile and stay a pure form. In particular the 404 is treated as its own
 * state, not an error: it means the account has no lawyer row, which is a
 * different problem from a failed request and needs different words.
 *
 * The form is keyed on the profile id so that if the identity behind the cache
 * ever changes - signing out and back in as a different lawyer without a full
 * reload - React remounts it with fresh defaults rather than keeping the
 * previous lawyer's values in an uncontrolled-looking way.
 */
export function LawyerProfileView() {
  const { profile, isPending, isError, error, isMissingProfile, refetch } =
    useMyProfile();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Profile"
        description="Keep your practice details current - this is what clients see when they search."
      />

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={6} />
          </CardContent>
        </Card>
      ) : isMissingProfile ? (
        <Card>
          <CardContent className="p-5">
            <EmptyState
              icon={UserX}
              title="No lawyer profile found"
              description="This account is registered as a lawyer but has no practice profile attached. Please contact support so it can be restored."
              action={
                <Button variant="outline" onClick={() => void refetch()}>
                  Try again
                </Button>
              }
            />
          </CardContent>
        </Card>
      ) : isError || !profile ? (
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load your profile"
            />
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          <ContactSection profile={profile} />
          <ProfileForm key={profile.id} profile={profile} />
        </div>
      )}
    </div>
  );
}
