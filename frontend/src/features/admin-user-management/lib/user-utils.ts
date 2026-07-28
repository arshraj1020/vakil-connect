import { ShieldCheck, Scale, UserRound, type LucideIcon } from "lucide-react";

import type { Role, UserSummaryResponse } from "@/types";

/**
 * Presentation rules for user accounts.
 *
 * No formatting is defined here - dates go through `lib/date`, numbers through
 * `lib/format`. What lives here is the mapping from the two enum-ish fields the
 * backend returns (`role`, `active`) to labels, colours and icons, plus the one
 * safety rule the frontend can enforce that the backend does not.
 */

export interface RoleMeta {
  label: string;
  /** Badge variant, expressed semantically rather than by hue. */
  intent: "default" | "secondary" | "info";
  icon: LucideIcon;
  description: string;
}

/**
 * Typed as a total Record, so adding a role to the backend enum fails to
 * compile here until it is handled - better than rendering it unstyled.
 */
export const ROLE_META: Record<Role, RoleMeta> = {
  CLIENT: {
    label: "Client",
    intent: "secondary",
    icon: UserRound,
    description: "Books consultations with lawyers.",
  },
  LAWYER: {
    label: "Lawyer",
    intent: "info",
    icon: Scale,
    description: "Offers consultations. Must be verified to appear in search.",
  },
  ADMIN: {
    label: "Admin",
    intent: "default",
    icon: ShieldCheck,
    description: "Full access to the administration portal.",
  },
};

export function getRoleMeta(role: Role): RoleMeta {
  return ROLE_META[role];
}

/** The role choices offered in the filter, plus "all". */
export const ROLE_FILTER_OPTIONS = [
  { value: "ALL", label: "All roles" },
  { value: "CLIENT", label: "Clients" },
  { value: "LAWYER", label: "Lawyers" },
  { value: "ADMIN", label: "Admins" },
] as const;

export type RoleFilter = (typeof ROLE_FILTER_OPTIONS)[number]["value"];

/**
 * Turns the filter selection into the query parameter.
 *
 * "ALL" becomes undefined rather than a literal, because the backend treats an
 * absent `role` as "every role" - sending `role=ALL` would fail enum binding
 * with a 400.
 */
export function toRoleParam(filter: RoleFilter): Role | undefined {
  return filter === "ALL" ? undefined : filter;
}

/** Account status, as the platform actually enforces it. */
export function getStatusMeta(active: boolean): {
  label: string;
  intent: "success" | "destructive";
  description: string;
} {
  return active
    ? {
        label: "Active",
        intent: "success",
        description: "Can sign in and use the platform.",
      }
    : {
        label: "Deactivated",
        intent: "destructive",
        description: "Blocked from signing in.",
      };
}

/**
 * Whether a row is the signed-in admin's own account.
 *
 * Compared by id, not email: both `CurrentUserResponse.id` and
 * `UserSummaryResponse.id` are `user.getId()`, so this is an exact identity
 * check rather than a string match that a case difference could break.
 */
export function isSelf(
  user: UserSummaryResponse,
  currentUserId: string | undefined,
): boolean {
  return currentUserId !== undefined && user.id === currentUserId;
}

/**
 * Whether the deactivate action should be offered for this row.
 *
 * A UX SAFEGUARD ONLY - explicitly NOT a security control.
 *
 * What it prevents: an admin clicking "Deactivate" on their own row by mistake.
 * `setUserActive` applies no guard of any kind, and `CustomUserDetailsService`
 * builds the principal with `.disabled(!active)`, so a self-deactivation locks
 * the admin out at their next sign-in with no way to undo it from the UI they
 * have just lost access to. That is a costly accident and cheap to prevent
 * here, from data the frontend already holds.
 *
 * What it does NOT prevent: anything deliberate. This check lives in the
 * browser, so `PUT /api/admin/users/{ownId}/deactivate` issued from curl, the
 * devtools console, or any other client succeeds exactly as before. Nothing
 * about this function makes the operation harder for someone who intends it.
 *
 * Two invariants therefore remain UNENFORCED, and both belong on the server:
 *
 *   1. An administrator must not be able to deactivate their own account.
 *   2. The platform must always retain at least one ACTIVE administrator.
 *
 * The second is beyond the frontend entirely. This guard covers the sole-admin
 * case only by coincidence - if exactly one admin exists it is the one signed
 * in - but with two admins, A can deactivate B and B can deactivate A, leaving
 * the platform with no administrator at all. Only the backend can hold that
 * line, because only the backend can count active admins inside the same
 * transaction as the write.
 *
 * See SECURITY-NOTES.md, "Authorization invariants".
 *
 * Activation is never blocked - restoring access is safe, including one's own.
 */
export function canDeactivate(
  user: UserSummaryResponse,
  currentUserId: string | undefined,
): boolean {
  return !isSelf(user, currentUserId);
}
