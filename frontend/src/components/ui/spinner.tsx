import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";

/**
 * Indeterminate activity indicator, for actions rather than content.
 *
 * Use a Skeleton when waiting for data that has a known shape; use a Spinner
 * inside buttons and short inline waits.
 */
export function Spinner({
  className,
  size = "default",
  label = "Loading",
}: {
  className?: string;
  size?: "sm" | "default" | "lg";
  label?: string;
}) {
  const sizeClass =
    size === "sm" ? "size-3" : size === "lg" ? "size-6" : "size-4";

  return (
    <Loader2
      role="status"
      aria-label={label}
      className={cn("animate-spin text-current", sizeClass, className)}
    />
  );
}
