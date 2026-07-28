"use client";

import { AtSign, CalendarDays, Phone, User } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatTimestamp } from "@/lib/date";
import type { UserSummaryResponse } from "@/types";

import { getRoleMeta, getStatusMeta, isSelf } from "../lib/user-utils";
import { UserActions } from "./user-actions";

/**
 * Everything the backend holds about one account.
 *
 * That is a complete statement, not a hedge: `UserSummaryResponse` has exactly
 * seven fields - id, fullName, email, phoneNumber, role, active, createdAt -
 * and all seven appear here. There is no richer user DTO anywhere in the API
 * and no endpoint returning one user by id, so nothing further exists to show.
 *
 * In particular there is deliberately no lawyer information for LAWYER accounts
 * - no verification state, no practice details. The user record carries no
 * lawyer id, so those cannot be looked up from here, and inventing a link would
 * misrepresent what an admin is actually looking at.
 *
 * Reuses the shared Dialog, so focus trapping, Escape and the overlay come from
 * Radix rather than being rebuilt.
 */
export function UserDetailsDialog({
  user,
  currentUserId,
  open,
  onOpenChange,
}: {
  user: UserSummaryResponse | null;
  currentUserId: string | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  if (!user) return null;

  const role = getRoleMeta(user.role);
  const status = getStatusMeta(user.active);

  const rows: Array<{ icon: LucideIcon; label: string; value: string }> = [
    { icon: User, label: "Full name", value: user.fullName },
    { icon: AtSign, label: "Email", value: user.email },
    {
      icon: Phone,
      label: "Phone",
      value: user.phoneNumber ?? "Not provided",
    },
    {
      icon: CalendarDays,
      label: "Registered",
      value: formatTimestamp(user.createdAt),
    },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{user.fullName}</DialogTitle>
          <DialogDescription>{role.description}</DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={role.intent}>{role.label}</Badge>
            <Badge variant={status.intent}>{status.label}</Badge>
            {isSelf(user, currentUserId) ? (
              <Badge variant="outline">Your account</Badge>
            ) : null}
          </div>

          <dl className="space-y-3">
            {rows.map((row) => {
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
          </dl>

          <p className="border-t border-border pt-4 text-xs text-muted-foreground">
            {status.description}
          </p>
        </div>

        <DialogFooter className="mt-2">
          <UserActions
            user={user}
            currentUserId={currentUserId}
            size="default"
          />
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
