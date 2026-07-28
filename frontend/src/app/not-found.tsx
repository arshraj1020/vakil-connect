import { FileQuestion } from "lucide-react";
import Link from "next/link";

import { ROUTES } from "@/lib/routes";

/**
 * 404 page.
 *
 * Lives at the app root rather than inside a route group so it also catches
 * paths that match no group at all - which is exactly the case that produced
 * Next's bare default page during testing.
 *
 * Intentionally standalone chrome: it cannot use the (public) or (protected)
 * layout, since a 404 has no way of knowing which side of the app the visitor
 * was aiming for. It offers both a public and a sign-in route rather than
 * guessing.
 */
export default function NotFound() {
  return (
    <div className="grid min-h-svh place-items-center px-4">
      <div className="flex max-w-md flex-col items-center gap-5 text-center">
        <span
          className="grid size-14 place-items-center rounded-full bg-muted text-muted-foreground"
          aria-hidden
        >
          <FileQuestion className="size-7" />
        </span>

        <div className="space-y-2">
          <p className="text-sm font-medium text-muted-foreground">404</p>
          <h1 className="text-2xl font-semibold tracking-tight">
            This page does not exist
          </h1>
          <p className="text-sm text-muted-foreground">
            The link may be out of date, or the page may have moved.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <Link
            href={ROUTES.HOME}
            className="inline-flex items-center justify-center rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
          >
            Go to the home page
          </Link>

          <Link
            href={ROUTES.LAWYERS}
            className="inline-flex items-center justify-center rounded-lg border border-border px-5 py-2.5 text-sm font-medium transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            Browse lawyers
          </Link>
        </div>
      </div>
    </div>
  );
}
