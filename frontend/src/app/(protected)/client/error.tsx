"use client";

import { AlertTriangle, RotateCcw } from "lucide-react";
import { useEffect } from "react";

import { Button } from "@/components/ui/button";

/**
 * Route-level error boundary for the client section.
 *
 * Scope: UNEXPECTED render errors. Failed requests are handled inline by each
 * widget with ErrorState, because those are both expected and recoverable per
 * widget - routing them here would blank the whole page when a single endpoint
 * is slow or down.
 *
 * `reset` re-renders the segment, which is enough to recover from a transient
 * render failure without a full reload.
 */
export default function ClientSectionError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Replace with a real reporter (Sentry et al.) when one is introduced.
    console.error("Client section error:", error);
  }, [error]);

  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <div className="flex max-w-sm flex-col items-center gap-4 text-center">
        <span className="grid size-12 place-items-center rounded-full bg-destructive/10 text-destructive">
          <AlertTriangle className="size-6" aria-hidden />
        </span>

        <div className="space-y-1">
          <h1 className="text-lg font-semibold">Something went wrong</h1>
          <p className="text-sm text-muted-foreground">
            This page could not be displayed. Please try again.
          </p>
        </div>

        <Button variant="outline" size="sm" onClick={reset}>
          <RotateCcw aria-hidden />
          Try again
        </Button>
      </div>
    </div>
  );
}
