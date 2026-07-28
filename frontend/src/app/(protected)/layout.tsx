"use client";

import { useRouter, usePathname } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { FullPageLoader } from "@/components/common/full-page-loader";
import { AppShell } from "@/components/layout/app-shell";
import { useAuth } from "@/features/auth/hooks/use-auth";
import { REDIRECT_PARAM } from "@/lib/constants";
import { ROUTES } from "@/lib/routes";

/**
 * Authenticated shell.
 *
 * Responsibilities, in order:
 *
 *  1. Block rendering while the session check is in flight. Returning the
 *     loader rather than `children` is what guarantees protected content never
 *     paints before authentication resolves.
 *  2. Send unauthenticated users to the login screen. Middleware already
 *     handles the case where no cookie exists; this additionally covers a
 *     cookie that turned out to be invalid, which only the backend can detect.
 *  3. Render the shared shell, which derives its navigation from the role.
 *
 * Role authorization is NOT done here - each role section wraps its own pages
 * in <RoleGuard>, so the shell can render around the message when a user
 * reaches the wrong section.
 */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated, isInitialising } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (isInitialising || isAuthenticated) return;

    const target = `${ROUTES.LOGIN}?${REDIRECT_PARAM}=${encodeURIComponent(pathname)}`;
    router.replace(target);
  }, [isAuthenticated, isInitialising, pathname, router]);

  if (isInitialising) {
    return <FullPageLoader label="Loading your workspace" />;
  }

  // Redirect is in flight; render nothing rather than a flash of the shell.
  if (!isAuthenticated || !user) {
    return <FullPageLoader label="Redirecting" />;
  }

  return <AppShell role={user.role}>{children}</AppShell>;
}
