import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";

/**
 * Occupies the viewport while the session check is in flight.
 *
 * Rendering this instead of `children` is what prevents protected content from
 * painting before authentication resolves.
 */
export function FullPageLoader({
  label = "Loading",
  className,
}: {
  label?: string;
  className?: string;
}) {
  return (
    <div
      className={cn("grid min-h-svh place-items-center bg-background", className)}
      role="status"
      aria-live="polite"
    >
      <div className="flex flex-col items-center gap-3">
        <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden />
        <span className="text-sm text-muted-foreground">{label}</span>
      </div>
    </div>
  );
}
