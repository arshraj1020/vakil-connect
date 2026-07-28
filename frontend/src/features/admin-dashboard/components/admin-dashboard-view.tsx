"use client";

import { ErrorState } from "@/components/common/error-state";
import {
  CardGridSkeleton,
  StatCardSkeleton,
} from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Card, CardContent } from "@/components/ui/card";

import { useAdminDashboard } from "../hooks/use-admin-dashboard";
import { AppointmentBreakdown } from "./appointment-breakdown";
import { PendingVerificationsCard } from "./pending-verifications-card";
import { QuickActions } from "./quick-actions";
import { StatsGrid } from "./stats-grid";

/**
 * Platform overview for administrators.
 *
 * Structured as labelled <section> landmarks, each introduced by an <h2>, so
 * the page can be navigated by heading or by region rather than as one
 * undifferentiated block. `PageHeader` supplies the <h1>.
 *
 * Failure is scoped rather than global. Analytics backs every number on the
 * page, so if it fails there is nothing to show and the screen becomes an error
 * with a retry. The pending queue is secondary: its failure is reported inside
 * its own card, leaving the statistics and the quick actions usable.
 *
 * Unauthorized and forbidden responses never reach this component. A 401 is
 * intercepted by the Axios layer, which clears the session and redirects to
 * login; a 403 is caught earlier still by <RoleGuard> in the /admin layout,
 * which renders the shared unauthorized state instead of these children. What
 * remains for ErrorState to handle here is network failure and 5xx, both of
 * which it already distinguishes.
 *
 * There is no "recent activity" section. The backend keeps no audit log and
 * exposes no timestamped event stream, and assembling one from unrelated
 * endpoints would be invented history. There is likewise no recent-users card:
 * `GET /api/admin/users` issues no ORDER BY and takes no sort parameter, so its
 * first page is an arbitrary set of accounts, not the newest ones.
 */
export function AdminDashboardView() {
  const {
    analytics,
    statusBreakdown,
    showPercentages,
    verificationRate,
    isPending,
    isError,
    error,
    refetch,
    pendingPreview,
    pendingTotal,
    isPendingPreviewLoading,
    isPendingPreviewError,
    pendingPreviewError,
    refetchPendingPreview,
  } = useAdminDashboard();

  if (isPending) {
    return (
      <div className="space-y-8">
        <PageHeader
          title="Admin dashboard"
          description="Platform activity at a glance."
        />
        <StatCardSkeleton count={8} />
        <CardGridSkeleton count={2} />
      </div>
    );
  }

  if (isError || !analytics) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Admin dashboard"
          description="Platform activity at a glance."
        />
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={refetch}
              title="Could not load platform statistics"
            />
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Admin dashboard"
        description="Platform activity at a glance."
      />

      <section aria-labelledby="overview-heading" className="space-y-4">
        <h2 id="overview-heading" className="text-lg font-semibold tracking-tight">
          Platform overview
        </h2>

        <StatsGrid analytics={analytics} verificationRate={verificationRate} />
      </section>

      <section aria-labelledby="attention-heading" className="space-y-4">
        <h2
          id="attention-heading"
          className="text-lg font-semibold tracking-tight"
        >
          Needs attention
        </h2>

        <div className="grid gap-4 lg:grid-cols-2">
          <PendingVerificationsCard
            lawyers={pendingPreview}
            total={pendingTotal}
            isLoading={isPendingPreviewLoading}
            isError={isPendingPreviewError}
            error={pendingPreviewError}
            onRetry={refetchPendingPreview}
          />

          <AppointmentBreakdown
            entries={statusBreakdown}
            total={analytics.totalAppointments}
            showPercentages={showPercentages}
          />
        </div>
      </section>

      <section aria-labelledby="actions-heading" className="space-y-4">
        <h2 id="actions-heading" className="text-lg font-semibold tracking-tight">
          Quick actions
        </h2>

        <QuickActions />
      </section>
    </div>
  );
}
