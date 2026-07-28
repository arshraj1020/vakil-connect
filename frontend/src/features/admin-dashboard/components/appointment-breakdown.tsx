"use client";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { StatusBreakdownEntry } from "../lib/dashboard-utils";

/** Semantic intent to bar colour. Mirrors the badge intents in the design system. */
const BAR_CLASS: Record<StatusBreakdownEntry["intent"], string> = {
  warning: "bg-warning",
  info: "bg-info",
  success: "bg-success",
  destructive: "bg-destructive",
  secondary: "bg-muted-foreground/40",
};

/**
 * How the platform's appointments are distributed across the five statuses.
 *
 * This is the ONE thing in the analytics payload that can be visualised
 * honestly. The five status counts are mutually exclusive and exhaustive, and
 * each is a whole-table COUNT, so they genuinely partition `totalAppointments`
 * - unlike a figure derived from one page of a paginated list.
 *
 * It is a proportion bar rather than a chart library: there is no time series
 * in the payload, so there is nothing to plot over time, and a pie chart of
 * five values communicates less than the numbers themselves.
 *
 * Accessibility: the bars are decorative and hidden, with the same information
 * carried in the visible table beside them - a text equivalent by construction
 * rather than an alt attribute bolted onto a graphic. `<dl>` gives each status
 * a term/value relationship.
 */
export function AppointmentBreakdown({
  entries,
  total,
  showPercentages,
}: {
  entries: StatusBreakdownEntry[];
  total: number;
  /**
   * False when the five counts do not sum to the total, in which case
   * percentages would not add to 100 and are suppressed rather than shown wrong.
   */
  showPercentages: boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Appointments by status</CardTitle>
        <CardDescription>
          {total > 0
            ? `All ${formatNumber(total)} appointments on the platform.`
            : "No appointments have been booked yet."}
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        {total > 0 && showPercentages ? (
          <div
            className="flex h-2 w-full overflow-hidden rounded-full bg-muted"
            aria-hidden
          >
            {entries
              .filter((entry) => entry.count > 0)
              .map((entry) => (
                <span
                  key={entry.key}
                  className={cn("h-full", BAR_CLASS[entry.intent])}
                  style={{ width: `${entry.percentage}%` }}
                />
              ))}
          </div>
        ) : null}

        <dl className="space-y-2">
          {entries.map((entry) => (
            <div
              key={entry.key}
              className="flex items-center justify-between gap-4 text-sm"
            >
              <dt className="inline-flex items-center gap-2 text-muted-foreground">
                <span
                  className={cn(
                    "size-2 shrink-0 rounded-full",
                    BAR_CLASS[entry.intent],
                  )}
                  aria-hidden
                />
                {entry.label}
              </dt>

              <dd className="tabular-nums font-medium">
                {formatNumber(entry.count)}
                {total > 0 && showPercentages ? (
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    {entry.percentage}%
                  </span>
                ) : null}
              </dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}
