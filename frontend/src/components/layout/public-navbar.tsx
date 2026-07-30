"use client";

import { LayoutDashboard } from "lucide-react";
import Link from "next/link";

import { useAuth } from "@/features/auth/hooks/use-auth";
import { ROUTES, dashboardFor } from "@/lib/routes";

import { Logo } from "./logo";
import { ThemeToggle } from "./theme-toggle";

/**
 * Navigation for the public side of the site.
 *
 * Session-aware, and it has to be. `/lawyers` is a single canonical route
 * serving signed-out visitors and signed-in clients alike, so an authenticated
 * user browsing it was previously shown a header whose two most prominent
 * controls were "Sign in" and "Get started" - which reads as having been
 * logged out and asked to sign in again.
 *
 * Making this component session-aware fixes that without splitting the route:
 * no /client/lawyers duplicate, no second copy of LawyerSearchView, and one URL
 * that can be shared, bookmarked and indexed. It fixes /lawyers/[id] for free.
 *
 * The cost is that this joins the client bundle. `(public)/layout.tsx` stays a
 * server component and renders this as a client child, so the pages themselves
 * keep their existing rendering behaviour.
 */
export function PublicNavbar() {
  const { user, isAuthenticated, isInitialising } = useAuth();

  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-16 items-center justify-between gap-4">
        <Logo />

        {/*
          Hidden below md, as before. The links are not lost on mobile: every
          one of them is also reachable from the footer, which is not
          breakpoint-gated. A mobile drawer is a Phase D concern.
        */}
        <nav className="hidden items-center gap-6 md:flex" aria-label="Main">
          <Link
            href={ROUTES.LAWYERS}
            className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            Find lawyers
          </Link>
          <Link
            href={ROUTES.ABOUT}
            className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            About
          </Link>
          <Link
            href={ROUTES.PRICING}
            className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            Pricing
          </Link>
        </nav>

        <div className="flex items-center gap-2">
          <ThemeToggle />

          {/*
            Three states, not two. While the session check is in flight the auth
            controls are held back behind a fixed-width placeholder: rendering
            "Sign in" first and swapping it for the user's name a moment later
            would flash the wrong state at every authenticated visitor, and
            collapsing the space would shift the layout as it resolved.
          */}
          {isInitialising ? (
            <div className="h-9 w-32" aria-hidden />
          ) : isAuthenticated && user ? (
            <Link
              href={dashboardFor(user.role)}
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-xs transition-opacity hover:opacity-90"
            >
              <LayoutDashboard className="size-4" aria-hidden />
              <span className="max-w-[8rem] truncate">
                {user.fullName.split(" ")[0] || "Dashboard"}
              </span>
            </Link>
          ) : (
            <>
              <Link
                href={ROUTES.LOGIN}
                className="rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
              >
                Sign in
              </Link>
              <Link
                href={ROUTES.REGISTER}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-xs transition-opacity hover:opacity-90"
              >
                Get started
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
