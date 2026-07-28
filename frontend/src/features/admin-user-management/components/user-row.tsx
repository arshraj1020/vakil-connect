"use client";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatTimestamp } from "@/lib/date";
import type { UserSummaryResponse } from "@/types";

import { getRoleMeta, getStatusMeta, isSelf } from "../lib/user-utils";
import { UserActions } from "./user-actions";

/**
 * One account.
 *
 * Every value shown is a field of `UserSummaryResponse`; nothing is derived or
 * inferred. Note what is absent and why: there is no "verified" indicator for
 * LAWYER rows, because verification lives on the Lawyer entity and this DTO has
 * no such field - nor any lawyer id with which to look one up.
 *
 * A plain region with explicit controls rather than one clickable surface: the
 * row carries both "view details" and a status action, and nesting a button
 * inside a button is invalid HTML with broken keyboard behaviour.
 *
 * The signed-in admin's own row is marked, so it is obvious why its deactivate
 * control is unavailable.
 */
export function UserRow({
  user,
  currentUserId,
  onViewDetails,
}: {
  user: UserSummaryResponse;
  currentUserId: string | undefined;
  onViewDetails: (user: UserSummaryResponse) => void;
}) {
  const role = getRoleMeta(user.role);
  const status = getStatusMeta(user.active);
  const self = isSelf(user, currentUserId);

  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center">
        <InitialsAvatar name={user.fullName} size="sm" />

        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="truncate font-medium">{user.fullName}</h3>

            {self ? (
              <Badge variant="outline" className="shrink-0">
                You
              </Badge>
            ) : null}
          </div>

          <p className="truncate text-xs text-muted-foreground">{user.email}</p>

          <p className="text-xs text-muted-foreground">
            Registered {formatTimestamp(user.createdAt)}
          </p>
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <Badge variant={role.intent}>{role.label}</Badge>
          <Badge variant={status.intent}>{status.label}</Badge>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onViewDetails(user)}
            aria-label={`View details for ${user.fullName}`}
          >
            Details
          </Button>

          <UserActions user={user} currentUserId={currentUserId} />
        </div>
      </CardContent>
    </Card>
  );
}
