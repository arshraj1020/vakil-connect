import api from "@/lib/axios";
import type { AdminUserParams, Paged, UserSummaryResponse } from "@/types";

/**
 * Admin user management. Exactly three endpoints exist.
 *
 * What the API does NOT provide, and therefore what this module cannot offer:
 *   - no single-user GET (`/api/users/me` returns the CALLER, not an arbitrary
 *     user), so there is no detail fetch - the list row is the whole record
 *   - no search by name or email
 *   - no ordering, and no sort parameter
 *   - no create, edit, delete, password reset or role change
 *   - no bulk operations
 *
 * Role filtering IS supported and is applied server-side, so it is a true
 * global filter rather than a filter over the loaded page.
 */

const ENDPOINTS = {
  users: "/api/admin/users",
  activate: (userId: string) => `/api/admin/users/${userId}/activate`,
  deactivate: (userId: string) => `/api/admin/users/${userId}/deactivate`,
} as const;

/**
 * One page of user accounts, optionally narrowed to a single role.
 *
 * `role` is bound by Spring to the `Role` enum, so an unrecognised value
 * produces a MethodArgumentTypeMismatchException, which the GlobalExceptionHandler
 * turns into a 400. The UI only ever sends CLIENT, LAWYER or ADMIN, so that
 * path is unreachable from here.
 *
 * ORDERING: none. `findAll(pageable)` and `findByRole(role, pageable)` both
 * receive `PageRequest.of(page, size)` with no Sort, so no ORDER BY is emitted
 * and row order is whatever Postgres returns - which shifts as rows are
 * updated, and `setUserActive` updates rows. Results must never be labelled
 * newest, oldest or alphabetical.
 */
export async function getUsers(
  params: AdminUserParams,
): Promise<Paged<UserSummaryResponse>> {
  const { data } = await api.get<Paged<UserSummaryResponse>>(ENDPOINTS.users, {
    params,
  });
  return data;
}

/**
 * Restores a user's ability to sign in.
 *
 * `CustomUserDetailsService` builds the principal with `.disabled(!active)`, so
 * this is precisely what gates authentication - nothing else changes.
 *
 * Not guarded by current state: activating an already-active user reassigns
 * true and returns 200, so a duplicate submission is harmless.
 *
 * Returns 404 for an unknown userId.
 */
export async function activateUser(
  userId: string,
): Promise<UserSummaryResponse> {
  const { data } = await api.put<UserSummaryResponse>(
    ENDPOINTS.activate(userId),
  );
  return data;
}

/**
 * Blocks a user from signing in.
 *
 * Existing sessions are NOT terminated: the flag is read when the principal is
 * loaded at authentication, so an already-issued JWT keeps working until it
 * expires. Deactivation prevents the next login rather than ending the current
 * session, and the UI says so rather than implying immediate lockout.
 *
 * The backend applies no guard whatsoever here - see `useUpdateUserStatus` for
 * the one protection the frontend can legitimately add.
 */
export async function deactivateUser(
  userId: string,
): Promise<UserSummaryResponse> {
  const { data } = await api.put<UserSummaryResponse>(
    ENDPOINTS.deactivate(userId),
  );
  return data;
}

export const adminUserService = {
  getUsers,
  activateUser,
  deactivateUser,
} as const;
