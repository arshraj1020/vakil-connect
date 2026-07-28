import type { AnalyticsResponse } from "@/types";

/**
 * Derived views over the analytics payload.
 *
 * Pure functions taking the response and returning presentation-ready shapes,
 * so the components stay declarative and the arithmetic is testable on its own.
 *
 * Everything here is derived from values the backend actually returns. Nothing
 * is estimated, extrapolated or sampled: each figure below is either copied
 * straight from `AnalyticsResponse` or is a sum/percentage of its fields, all of
 * which are whole-dataset COUNT queries.
 */

/** A single appointment status and its share of the total. */
export interface StatusBreakdownEntry {
  key: "pending" | "accepted" | "completed" | "rejected" | "cancelled";
  label: string;
  count: number;
  /** 0-100, rounded. Zero when there are no appointments at all. */
  percentage: number;
  /** Semantic intent, matching the shared appointment status colours. */
  intent: "warning" | "info" | "success" | "destructive" | "secondary";
}

/**
 * Appointment composition.
 *
 * The five statuses are mutually exclusive and exhaustive, so they always sum
 * to `totalAppointments` - which makes a percentage legitimate here, unlike a
 * figure derived from one page of a paginated list.
 *
 * Labels reuse the product's own vocabulary (ACCEPTED reads as "Confirmed",
 * REJECTED as "Declined") to match `lib/status.ts` rather than exposing raw
 * enum names on one screen and friendly ones everywhere else.
 */
export function selectStatusBreakdown(
  analytics: AnalyticsResponse,
): StatusBreakdownEntry[] {
  const total = analytics.totalAppointments;

  const share = (count: number) =>
    total > 0 ? Math.round((count / total) * 100) : 0;

  return [
    {
      key: "pending",
      label: "Pending",
      count: analytics.pendingAppointments,
      percentage: share(analytics.pendingAppointments),
      intent: "warning",
    },
    {
      key: "accepted",
      label: "Confirmed",
      count: analytics.acceptedAppointments,
      percentage: share(analytics.acceptedAppointments),
      intent: "info",
    },
    {
      key: "completed",
      label: "Completed",
      count: analytics.completedAppointments,
      percentage: share(analytics.completedAppointments),
      intent: "success",
    },
    {
      key: "rejected",
      label: "Declined",
      count: analytics.rejectedAppointments,
      percentage: share(analytics.rejectedAppointments),
      intent: "destructive",
    },
    {
      key: "cancelled",
      label: "Cancelled",
      count: analytics.cancelledAppointments,
      percentage: share(analytics.cancelledAppointments),
      intent: "secondary",
    },
  ];
}

/**
 * Sanity check on the five status counts.
 *
 * They are six independent COUNT queries, so nothing at the database level
 * forces them to agree with `totalAppointments`. They will agree in practice,
 * but if the enum ever gains a status that the analytics method forgets to
 * count, the breakdown would quietly understate the total. This lets the UI
 * suppress percentages rather than display ones that do not add up.
 */
export function isBreakdownConsistent(analytics: AnalyticsResponse): boolean {
  const summed =
    analytics.pendingAppointments +
    analytics.acceptedAppointments +
    analytics.completedAppointments +
    analytics.rejectedAppointments +
    analytics.cancelledAppointments;

  return summed === analytics.totalAppointments;
}

/**
 * Share of lawyers that have been verified, 0-100.
 *
 * Uses `verifiedLawyers + unverifiedLawyers` as the denominator rather than
 * `totalLawyers`. They are counted from different tables - the first two from
 * `lawyers`, the last from `users` where role = LAWYER - and a LAWYER account
 * without a lawyer row would make the two disagree. The lawyer-table figures
 * are the right denominator for a lawyer-table statistic.
 */
export function selectVerificationRate(analytics: AnalyticsResponse): number {
  const lawyersWithProfiles =
    analytics.verifiedLawyers + analytics.unverifiedLawyers;

  if (lawyersWithProfiles === 0) return 0;

  return Math.round((analytics.verifiedLawyers / lawyersWithProfiles) * 100);
}

/**
 * Whether any lawyer is waiting on an admin.
 *
 * Read from analytics rather than from the pending list's page metadata: the
 * count is a COUNT query over every unverified lawyer, whereas the list is one
 * page. Both agree, but only one is authoritative by construction.
 */
export function hasPendingVerifications(analytics: AnalyticsResponse): boolean {
  return analytics.unverifiedLawyers > 0;
}
