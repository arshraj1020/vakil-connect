import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";

/**
 * Content placeholder.
 *
 * Skeletons should mirror the shape of what is loading; a generic grey box
 * causes a visible layout shift when real content arrives.
 */
export function Skeleton({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("animate-pulse rounded-md bg-muted", className)}
      {...props}
    />
  );
}
