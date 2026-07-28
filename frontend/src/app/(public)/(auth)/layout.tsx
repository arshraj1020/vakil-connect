"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { FullPageLoader } from "@/components/common/full-page-loader";
import { Logo } from "@/components/layout/logo";
import { useAuth } from "@/features/auth/hooks/use-auth";
import { REDIRECT_PARAM } from "@/lib/constants";
import { dashboardFor } from "@/lib/routes";

/**
 * Shell for the sign-in and sign-up screens.
 *
 * A route group, so the URLs stay /login and /register while opting out of the
 * marketing navbar and footer - chrome that only competes with the form.
 *
 * It also owns the "already signed in" redirect for both routes. Middleware
 * cannot do this: the backend's JWT carries no role claim, so the edge has no
 * way to know which dashboard to send someone to. Placing it in this layout
 * rather than in each page keeps the rule in one place.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated, isInitialising } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (isInitialising || !isAuthenticated || !user) return;

    /*
     * Read the query string directly rather than with useSearchParams(): that
     * hook opts the whole subtree out of static rendering unless it sits behind
     * a Suspense boundary, and a layout cannot wrap itself in one. This runs in
     * an effect, so `window` is always defined.
     */
    const next = new URLSearchParams(window.location.search).get(REDIRECT_PARAM);
    router.replace(next ?? dashboardFor(user.role));
  }, [isAuthenticated, isInitialising, router, user]);

  // Hold the form back until the session check settles, so an authenticated
  // visitor never sees a login form flash before being redirected.
  if (isInitialising) {
    return <FullPageLoader label="Loading" />;
  }

  if (isAuthenticated) {
    return <FullPageLoader label="Redirecting" />;
  }

  return (
    <div className="relative flex min-h-svh flex-col items-center justify-center px-4 py-12">
      {/* Subtle brand wash; kept behind content and non-interactive. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_50%_0%,hsl(var(--primary)/0.08),transparent_70%)]"
      />

      <div className="mb-8">
        <Logo />
      </div>

      <div className="w-full max-w-md animate-fade-in">{children}</div>
    </div>
  );
}
