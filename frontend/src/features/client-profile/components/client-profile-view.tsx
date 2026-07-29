"use client";

import { AtSign, ShieldCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { getRoleMeta } from "@/lib/roles";

import { useMyClientProfile } from "../hooks/use-my-client-profile";
import { ClientProfileForm } from "./client-profile-form";

/**
 * Account screen for the signed-in client.
 *
 * Resolves the load states here so `ClientProfileForm` can take a guaranteed
 * profile and stay a pure form - the same split the lawyer profile uses.
 *
 * The read-only card comes first and is deliberately a description list rather
 * than disabled inputs: neither field can be changed by ANY endpoint a client
 * can reach, so rendering them as inputs would imply "editable but locked" and
 * invite a support question. `email` has no updater for any role, and `role` is
 * fixed at registration.
 *
 * The form is keyed on the account id so that if the identity behind the cache
 * ever changes, React remounts it with fresh defaults rather than keeping the
 * previous user's values.
 */
export function ClientProfileView() {
  const { profile, isPending, isError, error, refetch } = useMyClientProfile();

  const role = profile ? getRoleMeta(profile.role) : null;

  const readOnlyRows: Array<{ icon: LucideIcon; label: string; value: string }> =
    profile
      ? [{ icon: AtSign, label: "Email", value: profile.email }]
      : [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Profile"
        description="Manage the details lawyers see when you book a consultation."
      />

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={3} />
          </CardContent>
        </Card>
      ) : isError || !profile || !role ? (
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
        <>
          <Card>
            <CardHeader>
              <CardTitle>Account</CardTitle>
              <CardDescription>
                Managed by VakilConnect. Contact support to change these.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              <dl className="space-y-3">
                {readOnlyRows.map((row) => {
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
                      <dd className="break-all text-right text-sm font-medium">
                        {row.value}
                      </dd>
                    </div>
                  );
                })}

                <div className="flex items-start justify-between gap-4">
                  <dt className="inline-flex shrink-0 items-center gap-2 text-sm text-muted-foreground">
                    <ShieldCheck className="size-4" aria-hidden />
                    Account type
                  </dt>
                  <dd>
                    <Badge variant={role.intent}>{role.label}</Badge>
                  </dd>
                </div>
              </dl>

              <p className="border-t border-border pt-4 text-xs text-muted-foreground">
                {role.description}
              </p>
            </CardContent>
          </Card>

          <ClientProfileForm key={profile.id} profile={profile} />
        </>
      )}
    </div>
  );
}
