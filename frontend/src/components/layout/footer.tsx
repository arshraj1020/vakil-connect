import Link from "next/link";

import { ROUTES } from "@/lib/routes";
import { Logo } from "./logo";

/**
 * Marketing footer. Server component - no interactivity.
 *
 * THE LINKS ARE NOT DECORATION. The primary nav in PublicNavbar is `hidden
 * md:flex`, so below 768px it renders nothing at all - and there is no mobile
 * drawer yet. Without these, /about and /pricing would exist and be unreachable
 * on a phone unless someone typed the URL.
 *
 * A proper mobile navigation menu belongs to a later phase; this is the honest
 * minimum that keeps every public page reachable at every width.
 */

const LINKS = [
  { href: ROUTES.LAWYERS, label: "Find lawyers" },
  { href: ROUTES.ABOUT, label: "About" },
  { href: ROUTES.PRICING, label: "Pricing" },
] as const;

export function Footer() {
  return (
    <footer className="border-t border-border py-10">
      <div className="container flex flex-col items-center gap-6">
        <div className="flex w-full flex-col items-center justify-between gap-4 sm:flex-row">
          <Logo />

          <nav aria-label="Footer">
            <ul className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
              {LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>

        <p className="text-sm text-muted-foreground">
          &copy; {new Date().getFullYear()} VakilConnect. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
