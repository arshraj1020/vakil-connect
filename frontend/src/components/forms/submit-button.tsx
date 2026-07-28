"use client";

import type { ComponentProps, ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

/**
 * Submit control with a built-in busy state.
 *
 * Every mutation in the app needs the same three behaviours - disable while
 * pending, show a spinner, swap the label - and re-implementing them per form
 * is how double submissions get shipped. Disabling on `isPending` is the
 * safeguard: without it a slow booking request can be fired twice, and the
 * backend correctly rejects the second with a 409 the user cannot explain.
 */
export function SubmitButton({
  isPending = false,
  pendingLabel,
  children,
  className,
  disabled,
  ...props
}: ComponentProps<typeof Button> & {
  isPending?: boolean;
  pendingLabel?: string;
  children: ReactNode;
}) {
  return (
    <Button
      type="submit"
      disabled={disabled || isPending}
      className={cn(className)}
      aria-busy={isPending}
      {...props}
    >
      {isPending ? <Spinner size="sm" /> : null}
      {isPending && pendingLabel ? pendingLabel : children}
    </Button>
  );
}
