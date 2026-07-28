"use client";

import { useId, type ReactNode } from "react";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/**
 * Label + control + error message, wired together.
 *
 * Login and register share little structurally, but they repeat this field
 * scaffolding roughly fifteen times between them. Extracting it here means the
 * accessibility wiring is written once and correctly:
 *
 *  - a generated id links label to control,
 *  - `aria-describedby` points at the error text when present,
 *  - `aria-invalid` drives the error styling already built into Input,
 *    Textarea and SelectTrigger, so no component needs an `error` prop.
 *
 * `children` is a render prop rather than a slot because the control needs the
 * generated ids; passing them down keeps the caller free to use any control.
 */
export function FormField({
  label,
  error,
  hint,
  required = false,
  className,
  children,
}: {
  label: string;
  /** Message from Zod or the server. Presence marks the field invalid. */
  error?: string;
  /** Helper text shown when there is no error. */
  hint?: string;
  required?: boolean;
  className?: string;
  children: (props: {
    id: string;
    "aria-invalid": boolean;
    "aria-describedby": string | undefined;
  }) => ReactNode;
}) {
  const id = useId();
  const describedById = `${id}-description`;
  const hasError = Boolean(error);

  return (
    <div className={cn("space-y-2", className)}>
      <Label htmlFor={id}>
        {label}
        {required ? (
          <span className="ml-0.5 text-destructive" aria-hidden>
            *
          </span>
        ) : null}
      </Label>

      {children({
        id,
        "aria-invalid": hasError,
        "aria-describedby": hasError || hint ? describedById : undefined,
      })}

      {hasError ? (
        <p id={describedById} className="text-xs text-destructive" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={describedById} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
