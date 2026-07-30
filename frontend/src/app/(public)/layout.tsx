import type { ReactNode } from "react";

import { Footer } from "@/components/layout/footer";
import { PublicNavbar } from "@/components/layout/public-navbar";

/**
 * Marketing and unauthenticated shell.
 *
 * Stays a server component: nothing here depends on the session, so the public
 * side of the site is not pushed into the client bundle. Shares Logo and
 * ThemeToggle with the application shell.
 */
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col">
      <PublicNavbar />
      <main id="main-content" tabIndex={-1} className="flex-1 outline-none">
        {children}
      </main>
      <Footer />
    </div>
  );
}
