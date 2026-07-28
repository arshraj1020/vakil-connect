import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * A titled group of related fields.
 *
 * Long forms - lawyer registration in particular, which collects account and
 * professional details together - become unreadable as a flat column of
 * inputs. Grouping is presentation, so it lives here rather than in each form.
 *
 * Owns the section typography (text-lg/semibold) and the 4-unit field rhythm.
 */
export function FormSection({
  title,
  description,
  children,
  className,
}: {
  title?: string;
  description?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("space-y-4", className)}>
      {title || description ? (
        <div className="space-y-1">
          {title ? <h2 className="text-lg font-semibold tracking-tight">{title}</h2> : null}
          {description ? (
            <p className="text-sm text-muted-foreground">{description}</p>
          ) : null}
        </div>
      ) : null}

      <div className="space-y-4">{children}</div>
    </section>
  );
}

/** Places two fields side by side from `sm` up, stacked below it. */
export function FormRow({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("grid gap-4 sm:grid-cols-2", className)}>{children}</div>
  );
}
