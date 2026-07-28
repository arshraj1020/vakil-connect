import Link from "next/link";

import { ROUTES } from "@/lib/routes";

import { Logo } from "./logo";
import { ThemeToggle } from "./theme-toggle";

/**
 * Marketing-side navigation.
 *
 * A server component: it renders no session-dependent content, so it stays out
 * of the client bundle. Session-aware entry points belong on the pages
 * themselves, which can redirect an already-authenticated visitor.
 */
export function PublicNavbar() {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-16 items-center justify-between gap-4">
        <Logo />

        <nav className="hidden items-center gap-6 md:flex" aria-label="Main">
          <Link
            href={ROUTES.LAWYERS}
            className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            Find lawyers
          </Link>
        </nav>

        <div className="flex items-center gap-2">
          <ThemeToggle />
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
        </div>
      </div>
    </header>
  );
}
