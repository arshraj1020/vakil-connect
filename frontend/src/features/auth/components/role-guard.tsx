"use client";

import type { ReactNode } from "react";

import { FullPageLoader } from "@/components/common/full-page-loader";
import { UnauthorizedState } from "@/components/common/unauthorized-state";
import { dashboardFor } from "@/lib/routes";
import { useAuth } from "@/features/auth/hooks/use-auth";
import type { Role } from "@/types";

/**
 * Authorizes a section against a role.
 *
 * This is the ONLY place role authorization happens on the client. Middleware
 * cannot do it: the backend's JWT carries no role claim, so the edge can only
 * confirm that a token exists. The role becomes known once `/api/users/me`
 * resolves during hydration, which is the data this guard reads.
 *
 * Rendered inside the app shell, so a wrong-role visit shows the usual chrome
 * with an explanation and a route home rather than a bare error page.
 */
export function RoleGuard({
  allow,
  children,
}: {
  allow: Role;
  children: ReactNode;
}) {
  const { user, role, isInitialising, isAuthenticated } = useAuth();

  // Defensive: the protected layout already gates on this, but the guard must
  // be safe to use on its own.
  if (isInitialising) {
    return <FullPageLoader label="Checking permissions" />;
  }

  // The protected layout owns the redirect; rendering nothing avoids a flash
  // of either content or an error while it navigates.
  if (!isAuthenticated || !user) {
    return null;
  }

  if (role !== allow) {
    return <UnauthorizedState homeHref={dashboardFor(user.role)} />;
  }

  return <>{children}</>;
}
