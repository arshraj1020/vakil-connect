"use client";

import { useMemo } from "react";

import type { UserSummaryResponse } from "@/types";

/**
 * The record behind an opened row.
 *
 * Deliberately NOT a query. There is no endpoint that returns one user by id:
 * the admin API exposes only the paged list, and `/api/users/me` returns the
 * CALLER rather than an arbitrary user. So there is nothing to fetch.
 *
 * That costs nothing in completeness. `UserSummaryResponse` is the whole record
 * the backend holds for a user - id, fullName, email, phoneNumber, role, active
 * and createdAt - and every field of it is already in the row. A detail fetch
 * would return exactly what the caller already has.
 *
 * Resolving through the current page by id (rather than holding a snapshot of
 * the row object) keeps the dialog honest after a status change: the mutation
 * invalidates the page, the refetched rows flow back in, and the dialog's badge
 * updates in step with the row behind it. Holding a copy would leave the dialog
 * showing "Active" for an account just deactivated from inside it.
 */
export function useUserDetails(
  users: UserSummaryResponse[],
  selectedId: string | null,
): UserSummaryResponse | null {
  return useMemo(() => {
    if (!selectedId) return null;
    return users.find((user) => user.id === selectedId) ?? null;
  }, [users, selectedId]);
}
