"use client";

import { Pencil, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatWindow } from "@/lib/availability";
import type { DayAvailability } from "@/lib/availability";
import { cn } from "@/lib/utils";
import type { AvailabilityResponse } from "@/types";

/**
 * One weekday and its windows.
 *
 * Rendered for every day of the week, including days with none, so a lawyer can
 * see at a glance where they are unavailable and add hours there directly -
 * omitting empty days would hide the very action most likely to be wanted.
 *
 * The backend permits multiple windows per day (there is no uniqueness beyond
 * an exact duplicate), so all of them are listed and individually editable.
 */
export function DayAvailabilityCard({
  day,
  busyWindowId,
  onAdd,
  onEdit,
  onRemove,
}: {
  day: DayAvailability;
  /** The window currently being mutated, so only it shows a busy state. */
  busyWindowId: string | null;
  onAdd: (day: DayAvailability) => void;
  onEdit: (window: AvailabilityResponse) => void;
  onRemove: (window: AvailabilityResponse) => void;
}) {
  const hasWindows = day.windows.length > 0;

  return (
    <Card className={cn(!hasWindows && "border-dashed")}>
      <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1 space-y-2">
          <h3 className="text-sm font-semibold">{day.label}</h3>

          {hasWindows ? (
            <ul className="space-y-2">
              {day.windows.map((window) => {
                const isBusy = busyWindowId === window.id;

                return (
                  <li
                    key={window.id}
                    className={cn(
                      "flex items-center justify-between gap-3 rounded-lg bg-muted/50 px-3 py-2 transition-opacity",
                      isBusy && "pointer-events-none opacity-60",
                    )}
                  >
                    <span className="text-sm">{formatWindow(window)}</span>

                    <span className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => onEdit(window)}
                        disabled={isBusy}
                        aria-label={`Edit ${day.label} ${formatWindow(window)}`}
                      >
                        <Pencil aria-hidden />
                      </Button>

                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => onRemove(window)}
                        disabled={isBusy}
                        aria-label={`Remove ${day.label} ${formatWindow(window)}`}
                        className="text-muted-foreground hover:text-destructive"
                      >
                        <Trash2 aria-hidden />
                      </Button>
                    </span>
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">Not available</p>
          )}
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => onAdd(day)}
          className="shrink-0"
        >
          <Plus aria-hidden />
          Add hours
        </Button>
      </CardContent>
    </Card>
  );
}
