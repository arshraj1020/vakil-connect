import api from "@/lib/axios";
import type {
  AnalyticsResponse,
  LawyerSummaryResponse,
  Paged,
  PageParams,
} from "@/types";

/**
 * Read endpoints backing the admin dashboard. Both require ROLE_ADMIN.
 *
 * `GET /api/admin/dashboard` and `GET /api/admin/analytics` are the SAME
 * resource: the controller has both methods delegate to the identical
 * `adminService.getAnalytics()` and return the identical `AnalyticsResponse`.
 * The frontend standardises on `/api/admin/dashboard` and never calls the
 * analytics alias - one resource, one service method, one cache entry. Adding a
 * second wrapper later would split the cache in two and let the same data go
 * stale in one place while fresh in the other.
 *
 * Mutations (verify lawyer, activate/deactivate user, delete review) belong to
 * their own screens and are deliberately absent - the dashboard is read-only.
 */

const ENDPOINTS = {
  dashboard: "/api/admin/dashboard",
  pendingLawyers: "/api/admin/lawyers/pending",
} as const;

/**
 * Platform-wide counts.
 *
 * Every value is computed server-side with a COUNT query, so all fourteen are
 * authoritative for the whole dataset rather than a sample.
 *
 * One caveat worth knowing: `averagePlatformRating` is a mean OF MEANS - it
 * averages each lawyer's own average across lawyers having at least one review,
 * so it is not weighted by how many reviews each lawyer has. A lawyer with one
 * 5-star review counts as much as one with two hundred. It is labelled
 * accordingly in the UI.
 *
 * Named for the route rather than the DTO, matching `getClientDashboard` and
 * `getLawyerDashboard` in `appointment-service`.
 */
export async function getDashboard(): Promise<AnalyticsResponse> {
  const { data } = await api.get<AnalyticsResponse>(ENDPOINTS.dashboard);
  return data;
}

/**
 * Lawyers awaiting verification.
 *
 * Backed by `findByVerifiedFalse(pageable)`, which carries NO ordering, and the
 * controller exposes no sort parameter - so a page is an arbitrary slice of the
 * queue, not the oldest or newest applications. The dashboard therefore shows
 * it as an unranked preview and never claims recency.
 *
 * `LawyerSummaryResponse` has no `createdAt`, so an application date cannot be
 * displayed here even though `UserSummaryResponse` carries one.
 */
export async function getPendingLawyers(
  params: PageParams,
): Promise<Paged<LawyerSummaryResponse>> {
  const { data } = await api.get<Paged<LawyerSummaryResponse>>(
    ENDPOINTS.pendingLawyers,
    { params },
  );
  return data;
}

export const adminDashboardService = {
  getDashboard,
  getPendingLawyers,
} as const;
