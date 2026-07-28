import Link from "next/link";
import { ShieldAlert } from "lucide-react";

/**
 * Shown when an authenticated user reaches a section belonging to another role.
 *
 * Deliberately offers a way back to their own dashboard rather than dead-ending.
 * Will be restyled with Card/Button once the design system lands.
 */
export function UnauthorizedState({
  homeHref,
  description = "You do not have access to this area.",
}: {
  homeHref: string;
  description?: string;
}) {
  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <div className="flex max-w-sm flex-col items-center gap-4 text-center">
        <span className="grid size-12 place-items-center rounded-full bg-destructive/10 text-destructive">
          <ShieldAlert className="size-6" aria-hidden />
        </span>

        <div className="space-y-1">
          <h1 className="text-lg font-semibold">Access denied</h1>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>

        <Link
          href={homeHref}
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
        >
          Go to your dashboard
        </Link>
      </div>
    </div>
  );
}
