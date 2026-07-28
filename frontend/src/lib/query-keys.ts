import type { AdminUserParams, LawyerSearchParams, PageParams } from "@/types";

/**
 * Typed TanStack Query key factory.
 *
 * Keys are built here and nowhere else. Two properties matter:
 *
 * 1. Every factory returns an `as const` tuple, so keys carry literal types
 *    instead of `string[]`. A typo is a compile error.
 *
 * 2. Keys are HIERARCHICAL, and TanStack matches by prefix. `appointments.all`
 *    is a prefix of `appointments.client()`, so invalidating the former clears
 *    the whole subtree. Reviews and availability are nested under
 *    `lawyers.detail(id)` so that invalidating one lawyer also invalidates the
 *    data hanging off that lawyer.
 *
 * Object parameters are safe to embed: TanStack hashes keys with a stable
 * serialiser, so `{ city: "Mumbai", page: 0 }` and `{ page: 0, city: "Mumbai" }`
 * resolve to the same cache entry.
 */

const authKeys = {
  all: ["auth"] as const,
  /** The authenticated session; the source of truth for who is logged in. */
  currentUser: () => [...authKeys.all, "current-user"] as const,
};

const lawyerKeys = {
  all: ["lawyers"] as const,

  /** Public search. Invalidated when an admin verifies a lawyer. */
  search: (params: LawyerSearchParams) =>
    [...lawyerKeys.all, "search", params] as const,

  details: () => [...lawyerKeys.all, "detail"] as const,
  detail: (lawyerId: string) => [...lawyerKeys.details(), lawyerId] as const,

  /** Nested under the lawyer so invalidating the lawyer clears these too. */
  reviews: (lawyerId: string, params: PageParams) =>
    [...lawyerKeys.detail(lawyerId), "reviews", params] as const,
  availability: (lawyerId: string) =>
    [...lawyerKeys.detail(lawyerId), "availability"] as const,

  /** The authenticated lawyer's own profile (distinct from a public detail). */
  myProfile: () => [...lawyerKeys.all, "me"] as const,
};

const availabilityKeys = {
  all: ["availability"] as const,
  /** The authenticated lawyer's own windows, managed from their dashboard. */
  mine: () => [...availabilityKeys.all, "me"] as const,
};

const appointmentKeys = {
  all: ["appointments"] as const,
  /** Unpaged: the backend returns a plain array for both listings. */
  client: () => [...appointmentKeys.all, "client"] as const,
  lawyer: () => [...appointmentKeys.all, "lawyer"] as const,
};

const dashboardKeys = {
  all: ["dashboard"] as const,
  client: () => [...dashboardKeys.all, "client"] as const,
  lawyer: () => [...dashboardKeys.all, "lawyer"] as const,
  admin: () => [...dashboardKeys.all, "admin"] as const,
};

const adminKeys = {
  all: ["admin"] as const,
  pendingLawyers: (params: PageParams) =>
    [...adminKeys.all, "pending-lawyers", params] as const,
  users: (params: AdminUserParams) => [...adminKeys.all, "users", params] as const,
  reviews: (params: PageParams) => [...adminKeys.all, "reviews", params] as const,

  /*
   * There is deliberately no `analytics()` key here. `GET /api/admin/dashboard`
   * and `GET /api/admin/analytics` are one resource served under two names, and
   * caching it under two keys would let the same data be fresh in one place and
   * stale in another. It lives at `dashboard.admin()` alongside the client and
   * lawyer dashboards, which are its true siblings.
   */
};

export const queryKeys = {
  auth: authKeys,
  lawyers: lawyerKeys,
  availability: availabilityKeys,
  appointments: appointmentKeys,
  dashboard: dashboardKeys,
  admin: adminKeys,
} as const;

export type QueryKeys = typeof queryKeys;
