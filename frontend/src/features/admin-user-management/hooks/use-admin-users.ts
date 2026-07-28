"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { queryKeys } from "@/lib/query-keys";
import { adminUserService } from "@/services/admin-user-service";
import type { AdminUserParams } from "@/types";

/**
 * One page of user accounts.
 *
 * Uses the pre-existing `admin.users(params)` key. The role filter is part of
 * `params`, so each filter selection is its own cache entry - switching back to
 * a previously viewed filter is instant, and no client-side filtering is ever
 * performed.
 *
 * `keepPreviousData` holds the current rows while the next page or filter
 * loads, so the table does not collapse into a skeleton on every change.
 */
export function useAdminUsers(params: AdminUserParams) {
  const query = useQuery({
    queryKey: queryKeys.admin.users(params),
    queryFn: () => adminUserService.getUsers(params),
    placeholderData: keepPreviousData,
  });

  const users = useMemo(() => query.data?.content ?? [], [query.data]);
  const page = query.data?.page;

  return {
    ...query,
    users,
    totalPages: page?.totalPages ?? 0,
    totalElements: page?.totalElements ?? 0,
  };
}
