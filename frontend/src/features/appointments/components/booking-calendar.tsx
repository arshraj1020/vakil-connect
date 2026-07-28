"use client";

import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameMonth,
  startOfMonth,
  startOfWeek,
  subMonths,
} from "date-fns";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { isDateBookable } from "@/features/appointments/lib/slots";
import { cn } from "@/lib/utils";
import type { AvailabilityResponse } from "@/types";

/**
 * Month calendar restricted to dates this lawyer can actually be booked on.
 *
 * A native <input type="date"> supports `min` but cannot disable individual
 * WEEKDAYS, and availability here is a weekly pattern - a lawyer working only
 * Mondays must not have Tuesdays selectable. Hence a custom grid.
 *
 * A date is enabled only when both hold:
 *  - it is strictly after today (the backend validates @Future, which rejects
 *    today as well as the past),
 *  - its weekday has at least one available window.
 *
 * Every day is a real <button>, so keyboard navigation and disabled semantics
 * come from the platform rather than ARIA patched on top.
 */
const WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export function BookingCalendar({
  windows,
  value,
  onChange,
  className,
}: {
  windows: AvailabilityResponse[];
  /** Selected date as `yyyy-MM-dd`, or empty when nothing is chosen. */
  value: string;
  onChange: (isoDate: string) => void;
  className?: string;
}) {
  const [visibleMonth, setVisibleMonth] = useState(() =>
    startOfMonth(value ? new Date(value) : new Date()),
  );

  const weeks = useMemo(() => {
    // Pad to whole weeks so the grid is always 7 columns, starting Monday to
    // match the backend's DayOfWeek ordering.
    const start = startOfWeek(startOfMonth(visibleMonth), { weekStartsOn: 1 });
    const end = endOfWeek(endOfMonth(visibleMonth), { weekStartsOn: 1 });

    const days = eachDayOfInterval({ start, end });
    const grouped: Date[][] = [];

    for (let index = 0; index < days.length; index += 7) {
      grouped.push(days.slice(index, index + 7));
    }

    return grouped;
  }, [visibleMonth]);

  return (
    <div className={cn("space-y-3", className)}>
      <div className="flex items-center justify-between gap-2">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => setVisibleMonth((month) => subMonths(month, 1))}
          aria-label="Previous month"
        >
          <ChevronLeft aria-hidden />
        </Button>

        <p className="text-sm font-medium" aria-live="polite">
          {format(visibleMonth, "MMMM yyyy")}
        </p>

        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => setVisibleMonth((month) => addMonths(month, 1))}
          aria-label="Next month"
        >
          <ChevronRight aria-hidden />
        </Button>
      </div>

      <table className="w-full border-collapse">
        <thead>
          <tr>
            {WEEKDAY_LABELS.map((label) => (
              <th
                key={label}
                scope="col"
                className="pb-2 text-xs font-medium text-muted-foreground"
              >
                <span aria-hidden>{label}</span>
                <span className="sr-only">{label}</span>
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {weeks.map((week) => (
            <tr key={week[0]?.toISOString()}>
              {week.map((day) => {
                const isoDate = format(day, "yyyy-MM-dd");
                const inMonth = isSameMonth(day, visibleMonth);
                const bookable = isDateBookable(windows, isoDate);
                const selected = value === isoDate;

                return (
                  <td key={isoDate} className="p-0.5 text-center">
                    <button
                      type="button"
                      disabled={!bookable}
                      onClick={() => onChange(isoDate)}
                      aria-pressed={selected}
                      aria-label={format(day, "EEEE d MMMM yyyy")}
                      className={cn(
                        "grid size-9 w-full place-items-center rounded-lg text-sm transition-colors",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
                        "disabled:cursor-not-allowed disabled:opacity-30",
                        selected
                          ? "bg-primary font-medium text-primary-foreground"
                          : bookable
                            ? "hover:bg-accent hover:text-accent-foreground"
                            : "",
                        !inMonth && "text-muted-foreground/50",
                      )}
                    >
                      {format(day, "d")}
                    </button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>

      <p className="text-xs text-muted-foreground">
        Only dates matching this lawyer&apos;s weekly hours can be selected.
        Bookings must be at least one day ahead.
      </p>
    </div>
  );
}
