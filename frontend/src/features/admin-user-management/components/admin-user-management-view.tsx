"use client";

import { Users } from "lucide-react";
import { useMemo, useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { PaginationControls } from "@/components/common/pagination-controls";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAuth } from "@/features/auth/hooks/use-auth";
import { cn } from "@/lib/utils";

import { useAdminUsers } from "../hooks/use-admin-users";
import { useUserDetails } from "../hooks/use-user-details";
import {
  ROLE_FILTER_OPTIONS,
  toRoleParam,
  type RoleFilter,
} from "../lib/user-utils";
import { UserDetailsDialog } from "./user-details-dialog";
import { UsersTable } from "./users-table";

/** Matches the endpoint's own `defaultValue = "10"`. */
const PAGE_SIZE = 10;

/**
 * User management.
 *
 * The role filter is SERVER-SIDE - `GET /api/admin/users` takes a `role`
 * parameter bound to the Role enum - so it genuinely narrows the whole dataset
 * and the pagination beneath it stays correct. It is part of the query key, so
 * each selection is its own cache entry.
 *
 * There is deliberately NO search box. No endpoint accepts a name or email
 * query, and filtering the ten loaded rows while calling it "search" would hide
 * every match on every other page. Role is the only filter the backend can
 * honour, so it is the only one offered.
 *
 * Results are never described as newest, oldest or alphabetical: both
 * `findAll(pageable)` and `findByRole(role, pageable)` receive a Pageable with
 * no Sort, so no ordering contract exists. `createdAt` is shown per row because
 * it is real data, but the list is not claimed to be ordered by it.
 *
 * 401 and 403 do not reach here - the Axios interceptor handles 401, and
 * RoleGuard in the /admin layout catches non-admins before these children
 * mount. ErrorState covers network failure and 5xx.
 */
export function AdminUserManagementView() {
  const [page, setPage] = useState(0);
  const [roleFilter, setRoleFilter] = useState<RoleFilter>("ALL");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { user: currentUser } = useAuth();

  const params = useMemo(
    () => ({ page, size: PAGE_SIZE, role: toRoleParam(roleFilter) }),
    [page, roleFilter],
  );

  const {
    users,
    totalPages,
    totalElements,
    isPending,
    isFetching,
    isError,
    error,
    refetch,
  } = useAdminUsers(params);

  /* Re-read from the page by id, so the dialog tracks status changes. */
  const selected = useUserDetails(users, selectedId);

  const changeFilter = (next: RoleFilter) => {
    setRoleFilter(next);
    // A page index is meaningless across filters - "page 3 of clients" has no
    // counterpart in a shorter admin list, and would land on an empty page.
    setPage(0);
  };

  const isFiltered = roleFilter !== "ALL";

  return (
    <div className="space-y-6">
      <PageHeader
        title="User management"
        description="Review accounts and control who can sign in."
      />

      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 p-4">
          <div className="space-y-2">
            <Label htmlFor="role-filter">Role</Label>

            <Select
              value={roleFilter}
              onValueChange={(value) => changeFilter(value as RoleFilter)}
            >
              <SelectTrigger id="role-filter" className="w-48">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ROLE_FILTER_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {isFiltered ? (
            <Button variant="ghost" onClick={() => changeFilter("ALL")}>
              Clear filter
            </Button>
          ) : null}
        </CardContent>
      </Card>

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={6} />
          </CardContent>
        </Card>
      ) : isError ? (
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load user accounts"
            />
          </CardContent>
        </Card>
      ) : users.length === 0 ? (
        <Card>
          <CardContent className="p-5">
            {/*
             * Three distinguishable empty results, all read from real state
             * rather than guessed: no accounts of this role at all, no accounts
             * whatsoever, or a page that emptied beneath the admin.
             */}
            {totalElements === 0 ? (
              <EmptyState
                icon={Users}
                title={isFiltered ? "No accounts with this role" : "No accounts yet"}
                description={
                  isFiltered
                    ? "Choose a different role to see other accounts."
                    : "Accounts will appear here as people register."
                }
                action={
                  isFiltered ? (
                    <Button variant="outline" onClick={() => changeFilter("ALL")}>
                      Show all roles
                    </Button>
                  ) : undefined
                }
              />
            ) : (
              <EmptyState
                icon={Users}
                title="Nothing on this page"
                description="Go back to the first page to see the current list."
                action={
                  <Button variant="outline" onClick={() => setPage(0)}>
                    Back to first page
                  </Button>
                }
              />
            )}
          </CardContent>
        </Card>
      ) : (
        <div
          className={cn(
            "space-y-4 transition-opacity",
            isFetching && "opacity-60",
          )}
        >
          <UsersTable
            users={users}
            currentUserId={currentUser?.id}
            onViewDetails={(user) => setSelectedId(user.id)}
          />

          <PaginationControls
            page={page}
            size={PAGE_SIZE}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={setPage}
            itemLabel={isFiltered ? "matching accounts" : "accounts"}
          />
        </div>
      )}

      <UserDetailsDialog
        user={selected}
        currentUserId={currentUser?.id}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null);
        }}
      />
    </div>
  );
}
