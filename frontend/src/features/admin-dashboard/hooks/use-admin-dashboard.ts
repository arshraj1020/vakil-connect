"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { queryKeys } from "@/lib/query-keys";
import { adminDashboardService } from "@/services/admin-dashboard-service";
import type { PageParams } from "@/types";

import {
  isBreakdownConsistent,
  selectStatusBreakdown,
  selectVerificationRate,
} from "../lib/dashboard-utils";

/** Enough pending lawyers for a preview without crowding the card. */
const PREVIEW_SIZE = 5;

const PREVIEW_PARAMS: PageParams = { page: 0, size: PREVIEW_SIZE };

/**
 * Everything the admin dashboard reads.
 *
 * Two independent queries rather than one chained pair - neither depends on the
 * other's result, so they run in parallel and each renders as it arrives.
 *
 * Both use pre-existing keys (`dashboard.admin()`, `admin.pendingLawyers()`),
 * so verifying a lawyer on the verification screen invalidates this dashboard's
 * pending preview for free, with no coordination between the two features.
 *
 * The statistics are keyed at `dashboard.admin()` rather than under `admin.*`
 * because they are the admin sibling of the client and lawyer dashboards, and
 * because the resource has two route names - one key keeps it one cache entry.
 *
 * The analytics query is the page's backbone: if it fails there are no numbers
 * to show and the screen is an error. The pending preview is secondary - its
 * failure costs one card, so it is reported separately and the rest still
 * renders.
 */
export function useAdminDashboard() {
  const analyticsQuery = useQuery({
    queryKey: queryKeys.dashboard.admin(),
    queryFn: adminDashboardService.getDashboard,
  });

  const pendingQuery = useQuery({
    queryKey: queryKeys.admin.pendingLawyers(PREVIEW_PARAMS),
    queryFn: () => adminDashboardService.getPendingLawyers(PREVIEW_PARAMS),
  });

  const analytics = analyticsQuery.data;

  /*
   * Derived once per analytics payload. The arithmetic is trivial, but these
   * feed list renders, and a new array identity on every keystroke elsewhere on
   * the page would defeat memoisation downstream.
   */
  const statusBreakdown = useMemo(
    () => (analytics ? selectStatusBreakdown(analytics) : []),
    [analytics],
  );

  const showPercentages = useMemo(
    () => (analytics ? isBreakdownConsistent(analytics) : false),
    [analytics],
  );

  const verificationRate = useMemo(
    () => (analytics ? selectVerificationRate(analytics) : 0),
    [analytics],
  );

  const pendingPreview = useMemo(
    () => pendingQuery.data?.content ?? [],
    [pendingQuery.data],
  );

  return {
    analytics,
    statusBreakdown,
    showPercentages,
    verificationRate,

    isPending: analyticsQuery.isPending,
    isError: analyticsQuery.isError,
    error: analyticsQuery.error,
    refetch: () => {
      void analyticsQuery.refetch();
      void pendingQuery.refetch();
    },

    /** The preview list, reported separately so one card can fail alone. */
    pendingPreview,
    /** Authoritative queue size, from the COUNT - not the preview's length. */
    pendingTotal: analytics?.unverifiedLawyers ?? 0,
    isPendingPreviewLoading: pendingQuery.isPending,
    isPendingPreviewError: pendingQuery.isError,
    pendingPreviewError: pendingQuery.error,
    refetchPendingPreview: () => void pendingQuery.refetch(),
  };
}
