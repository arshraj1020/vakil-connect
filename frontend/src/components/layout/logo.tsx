import Link from "next/link";
import { Scale } from "lucide-react";

import { cn } from "@/lib/utils";
import { ROUTES } from "@/lib/routes";

/**
 * Wordmark, shared by the public and application navbars.
 *
 * One of the two primitives (with ThemeToggle) that both shells reuse, so the
 * brand is defined in exactly one place.
 */
export function Logo({
  className,
  href = ROUTES.HOME,
}: {
  className?: string;
  href?: string;
}) {
  return (
    <Link
      href={href}
      className={cn(
        "inline-flex items-center gap-2 font-semibold tracking-tight",
        "rounded-md transition-opacity hover:opacity-80",
        className,
      )}
      aria-label="VakilConnect home"
    >
      <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
        <Scale className="size-4" aria-hidden />
      </span>
      <span className="text-base">VakilConnect</span>
    </Link>
  );
}
