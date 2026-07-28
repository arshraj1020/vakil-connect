"use client";

import { AtSign, BadgeCheck, Phone, ScrollText, ShieldAlert, User } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { LawyerProfileResponse } from "@/types";

/**
 * Identity and credentials - read-only, every one of them.
 *
 * Nothing here is a form field, because no endpoint available to a LAWYER can
 * change any of it:
 *
 *   fullName, phoneNumber  owned by User; only PUT /api/client/profile writes
 *                          them, and SecurityConfig restricts /api/client/**
 *                          to hasRole("CLIENT") - a lawyer gets 403
 *   email                  no endpoint updates it, for any role
 *   barCouncilNumber       absent from UpdateLawyerProfileRequest by design;
 *                          it is a legal identifier, unique and set once
 *   verified               admin-only, via PUT /api/admin/lawyers/{id}/verify
 *
 * Rendering them as disabled inputs would imply they are editable-but-locked
 * and invite a support question. A description list says plainly that this is
 * reference information.
 */
export function ContactSection({ profile }: { profile: LawyerProfileResponse }) {
  const rows: Array<{ icon: LucideIcon; label: string; value: string }> = [
    { icon: User, label: "Full name", value: profile.fullName },
    { icon: AtSign, label: "Email", value: profile.email },
    {
      icon: Phone,
      label: "Phone",
      value: profile.phoneNumber ?? "Not provided",
    },
    {
      icon: ScrollText,
      label: "Bar council number",
      value: profile.barCouncilNumber,
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Account details</CardTitle>
        <CardDescription>
          Managed by VakilConnect. Contact support to change these.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <dl className="space-y-3">
          {rows.map((row) => {
            const Icon = row.icon;

            return (
              <div
                key={row.label}
                className="flex items-start justify-between gap-4"
              >
                <dt className="inline-flex items-center gap-2 text-sm text-muted-foreground">
                  <Icon className="size-4" aria-hidden />
                  {row.label}
                </dt>
                <dd className="break-all text-right text-sm font-medium">
                  {row.value}
                </dd>
              </div>
            );
          })}
        </dl>

        <div className="border-t border-border pt-4">
          {profile.verified ? (
            <Badge variant="success" className="gap-1.5">
              <BadgeCheck className="size-3.5" aria-hidden />
              Verified lawyer
            </Badge>
          ) : (
            <div className="space-y-2">
              <Badge variant="warning" className="gap-1.5">
                <ShieldAlert className="size-3.5" aria-hidden />
                Pending verification
              </Badge>
              <p className="text-xs text-muted-foreground">
                Your profile is hidden from search until an administrator
                verifies it. You can still edit it in the meantime.
              </p>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
