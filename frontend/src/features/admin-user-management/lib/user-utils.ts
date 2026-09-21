import { getRoleMeta } from "@/lib/roles";
import type { Role, UserSummaryResponse } from "@/types";

/**
 * Presentation rules for user accounts.
 *
 * No formatting is defined here - dates go through `lib/date`, numbers through
 * `lib/format`. Role presentation lives in `lib/roles.ts`, shared with the
 * client profile screen and re-exported below so this feature's components keep
 * a single import. What remains here is account STATUS presentation and the one
 * safety rule the frontend can enforce that the backend does not.
 */

// Re-exported so existing imports in this feature stay unchanged.
export { getRoleMeta };

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

/**
 * Whether the Delete action should be offered for this row.
 *
 * A UX SAFEGUARD, same status as `canDeactivate` and for the same reason:
 * this check runs in the browser only. The backend enforces both invariants
 * for real (`AdminServiceImpl.deleteUser`, 409 BusinessRuleException) -
 * deleting your own account or the last ADMIN fails server-side regardless
 * of what this function returns. This exists so the button reads as a rule
 * rather than a surprise 409 after the confirmation dialog.
 *
 * `adminCount` is the number of ADMIN accounts currently loaded on THIS
 * page - not a platform-wide count. That is a real limitation: with role
 * filtering or pagination, this guard can under- or over-count relative to
 * the backend's true count, which always sees every admin. It still catches
 * the common case (one admin, deleting yourself or the only other admin
 * visible) and never needs to be exact, because the server has the last
 * word either way.
 */
export function canDelete(
  user: UserSummaryResponse,
  currentUserId: string | undefined,
  adminCount: number,
): boolean {
  if (isSelf(user, currentUserId)) {
    return false;
  }

  if (user.role === "ADMIN" && adminCount <= 1) {
    return false;
  }

  return true;
}
