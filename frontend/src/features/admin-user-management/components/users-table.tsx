"use client";

import type { UserSummaryResponse } from "@/types";

import { UserRow } from "./user-row";

/**
 * The page of accounts.
 *
 * A semantic <ul> rather than a <table>: each row carries badges and two
 * controls, which a real table would either truncate or force to scroll
 * horizontally on mobile, and claiming grid semantics would oblige full
 * keyboard grid navigation to be correct.
 *
 * Rendered in the order received. `findAll(pageable)` and
 * `findByRole(role, pageable)` emit no ORDER BY and the controller passes no
 * Sort, so there is no ordering contract to honour and none is implied - the
 * screen never describes these rows as newest, oldest or alphabetical.
 */
export function UsersTable({
  users,
  currentUserId,
  onViewDetails,
}: {
  users: UserSummaryResponse[];
  currentUserId: string | undefined;
  onViewDetails: (user: UserSummaryResponse) => void;
}) {
  return (
    <ul className="space-y-3" aria-label="User accounts">
      {users.map((user) => (
        <li key={user.id}>
          <UserRow
            user={user}
            currentUserId={currentUserId}
            onViewDetails={onViewDetails}
          />
        </li>
      ))}
    </ul>
  );
}
