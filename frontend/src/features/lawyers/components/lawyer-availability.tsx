"use client";

import { CalendarClock } from "lucide-react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useLawyerAvailability } from "@/features/lawyers/hooks/use-lawyer-availability";
import { formatWindow } from "@/lib/availability";
import { cn } from "@/lib/utils";

/**
 * Weekly consultation hours.
 *
 * Requested only here, never in search results: availability is not part of
 * `LawyerSummaryResponse`, so rendering it on cards would cost one request per
 * card.
 *
 * All seven days are listed, including days with no windows, so a client can
 * see when the lawyer is unavailable rather than inferring it from absence.
 *
 * These are RECURRING WEEKLY patterns, not specific dates - a date is bookable
 * when its weekday has a matching window.
 */
export function LawyerAvailability({ lawyerId }: { lawyerId: string }) {
  const { days, isPending, isError, error, refetch, isBookable } =
    useLawyerAvailability(lawyerId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Consultation hours</CardTitle>
        <CardDescription>Weekly availability for this lawyer.</CardDescription>
      </CardHeader>

      <CardContent>
        {isPending ? (
          <div className="space-y-3">
            {Array.from({ length: 7 }, (_, index) => (
              <div key={index} className="flex items-center justify-between gap-4">
                <Skeleton className="h-3.5 w-24" />
                <Skeleton className="h-3.5 w-32" />
              </div>
            ))}
          </div>
        ) : isError ? (
          <ErrorState
            error={error}
            onRetry={() => void refetch()}
            title="Could not load availability"
          />
        ) : !isBookable ? (
          <EmptyState
            icon={CalendarClock}
            title="No hours published"
            description="This lawyer has not shared their consultation hours yet."
          />
        ) : (
          <ul className="divide-y divide-border">
            {days.map((day) => {
              const isAvailable = day.windows.length > 0;

              return (
                <li
                  key={day.day}
                  className="flex items-start justify-between gap-4 py-2.5 first:pt-0 last:pb-0"
                >
                  <span
                    className={cn(
                      "text-sm",
                      isAvailable
                        ? "font-medium"
                        : "text-muted-foreground",
                    )}
                  >
                    {day.label}
                  </span>

                  {isAvailable ? (
                    <span className="flex flex-col items-end gap-0.5 text-sm">
                      {day.windows.map((window) => (
                        <span key={window.id}>{formatWindow(window)}</span>
                      ))}
                    </span>
                  ) : (
                    <span className="text-sm text-muted-foreground">
                      Unavailable
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
