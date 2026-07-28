"use client";

import type { DayAvailability } from "@/lib/availability";
import type { AvailabilityResponse } from "@/types";

import { DayAvailabilityCard } from "./day-availability-card";

/**
 * The full week.
 *
 * A semantic list so assistive technology announces it as seven items, and so
 * the days keep a meaningful reading order.
 */
export function WeeklySchedule({
  days,
  busyWindowId,
  onAdd,
  onEdit,
  onRemove,
}: {
  days: DayAvailability[];
  busyWindowId: string | null;
  onAdd: (day: DayAvailability) => void;
  onEdit: (window: AvailabilityResponse) => void;
  onRemove: (window: AvailabilityResponse) => void;
}) {
  return (
    <ul className="space-y-3" aria-label="Weekly consultation hours">
      {days.map((day) => (
        <li key={day.day}>
          <DayAvailabilityCard
            day={day}
            busyWindowId={busyWindowId}
            onAdd={onAdd}
            onEdit={onEdit}
            onRemove={onRemove}
          />
        </li>
      ))}
    </ul>
  );
}
