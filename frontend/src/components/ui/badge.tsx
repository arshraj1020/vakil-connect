import { cva, type VariantProps } from "class-variance-authority";
import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";

/**
 * Badge variants.
 *
 * Variants are SEMANTIC (success/warning/info) rather than chromatic
 * (green/amber/blue), so a caller expresses meaning and the palette can change
 * without touching call sites. Backgrounds use a 10-15% token tint with a
 * full-strength foreground, which keeps contrast legible in both themes.
 */
export const badgeVariants = cva(
  cn(
    "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5",
    "text-xs font-medium whitespace-nowrap transition-colors",
    "[&_svg]:size-3 [&_svg]:shrink-0",
  ),
  {
    variants: {
      variant: {
        default: "bg-primary/10 text-primary",
        secondary: "bg-muted text-muted-foreground",
        outline: "border border-border text-foreground",
        success: "bg-success/10 text-success",
        warning: "bg-warning/15 text-warning",
        destructive: "bg-destructive/10 text-destructive",
        info: "bg-info/10 text-info",
      },
    },
    defaultVariants: { variant: "default" },
  },
);

export interface BadgeProps
  extends ComponentProps<"span">,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
