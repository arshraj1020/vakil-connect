"use client";

import { AlertTriangle, RotateCcw } from "lucide-react";
import Link from "next/link";
import { useEffect } from "react";

import { Button } from "@/components/ui/button";

/**
 * The body of every route-level `error.tsx`.
 *
 * SCOPE: UNEXPECTED RENDER ERRORS ONLY. A failed request is handled inline by
 * the widget that made it, using `ErrorState` - those are expected, recoverable
 * per widget, and routing them here would blank a whole page because one
 * endpoint was slow. What reaches an error boundary is a component that threw
 * while rendering, which is a bug rather than a condition.
 *
 * WHY THIS COMPONENT EXISTS. Next requires one `error.tsx` per segment, and
 * before Phase D the client and lawyer sections held two near-identical copies.
 * Four sections meant four copies drifting apart; the differences that matter
 * are the label and where "somewhere safe" points, so those are props and
 * nothing else is.
 *
 * NO STACK TRACE IS RENDERED. `error.message` can carry internals - a query
 * string, a path, an upstream error - and none of it helps the person reading
 * it. `digest` is shown instead: it is the server-side identifier Next assigns,
 * which is meaningless to an attacker and exactly what a support conversation
 * needs to find the log line.
 */
export function SectionError({
  error,
  reset,
  scope,
  homeHref,
  homeLabel,
}: {
  error: Error & { digest?: string };
  reset: () => void;
  /** Names the failing area in the console log, e.g. "Admin section". */
  scope: string;
  /** Where "somewhere safe" leads for this audience. */
  homeHref: string;
  homeLabel: string;
}) {
  useEffect(() => {
    /*
     * Development only. In production this is noise at best: the user cannot
     * act on it, and the server has already logged the same failure against the
     * digest shown below. When a real reporter (Sentry et al.) is introduced it
     * is called here, unconditionally, and this guard goes away.
     */
    if (process.env.NODE_ENV !== "production") {
      console.error(`${scope} error:`, error);
    }
  }, [error, scope]);

  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <div className="flex max-w-md flex-col items-center gap-4 text-center">
        <span className="grid size-12 place-items-center rounded-full bg-destructive/10 text-destructive">
          <AlertTriangle className="size-6" aria-hidden />
        </span>

        <div className="space-y-1">
          <h1 className="text-lg font-semibold">This page didn&apos;t load</h1>
          <p className="text-sm text-muted-foreground">
            Something went wrong while displaying it. Your data is safe and
            nothing was lost — trying again usually works.
          </p>
        </div>

        {/*
         * Two routes out, because `reset` is not always enough: it re-renders
         * the same segment, so an error caused by state that is still there
         * will simply throw again. The second action leaves the segment
         * entirely, which always works.
         */}
        <div className="flex flex-col gap-2 sm:flex-row">
          <Button variant="outline" size="sm" onClick={reset}>
            <RotateCcw aria-hidden />
            Try again
          </Button>

          <Button variant="ghost" size="sm" asChild>
            <Link href={homeHref}>{homeLabel}</Link>
          </Button>
        </div>

        {error.digest ? (
          <p className="text-xs text-muted-foreground">
            Reference code:{" "}
            <span className="font-mono">{error.digest}</span>
          </p>
        ) : null}
      </div>
    </div>
  );
}
