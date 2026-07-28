import { formatTime } from "./date";
import type { AvailabilityResponse, DayOfWeek } from "@/types";

/**
 * Presentation helpers for weekly availability windows.
 *
 * Shared between the public lawyer profile and, later, the screen where a
 * lawyer manages their own windows - so weekday ordering and window formatting
 * are defined once.
 */

/** Calendar order. The API returns windows sorted, but never rely on that for display. */
export const DAY_ORDER: readonly DayOfWeek[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: "Monday",
  TUESDAY: "Tuesday",
  WEDNESDAY: "Wednesday",
  THURSDAY: "Thursday",
  FRIDAY: "Friday",
  SATURDAY: "Saturday",
  SUNDAY: "Sunday",
};

export function formatDayOfWeek(day: DayOfWeek): string {
  return DAY_LABELS[day];
}

/** "10:00 AM - 1:00 PM" */
export function formatWindow(window: AvailabilityResponse): string {
  return `${formatTime(window.startTime)} - ${formatTime(window.endTime)}`;
}

export interface DayAvailability {
  day: DayOfWeek;
  label: string;
  windows: AvailabilityResponse[];
}

/**
 * Groups windows by weekday in calendar order.
 *
 * Every weekday is present, including days with no windows, so the profile can
 * render a complete week and show which days are unavailable rather than
 * silently omitting them.
 */
export function groupByDay(
  windows: AvailabilityResponse[],
): DayAvailability[] {
  return DAY_ORDER.map((day) => ({
    day,
    label: DAY_LABELS[day],
    windows: windows
      .filter((window) => window.dayOfWeek === day && window.available)
      .sort((a, b) => a.startTime.localeCompare(b.startTime)),
  }));
}

/** Whether the lawyer has published any bookable window at all. */
export function hasAnyAvailability(windows: AvailabilityResponse[]): boolean {
  return windows.some((window) => window.available);
}
