"use client";

import { CalendarOff } from "lucide-react";

import { EmptyState } from "@/components/common/empty-state";
import { formatTime } from "@/lib/date";
import { cn } from "@/lib/utils";

/**
 * Choice of start time within the lawyer's hours for the selected date.
 *
 * Slots are generated at a fixed interval by `generateSlots`; the backend has
 * no notion of appointment length, so the grid is purely a frontend
 * convention.
 *
 * IMPORTANT LIMITATION: only slots the SIGNED-IN client already holds can be
 * disabled up front. No endpoint exposes another client's bookings, so a slot
 * taken by someone else looks free until submission, where the backend rejects
 * it with 409 from a database-level unique index. The booking view surfaces
 * that as a recoverable message rather than a failure.
 */
export function TimeSlotPicker({
  slots,
  takenSlots,
  value,
  onChange,
  className,
}: {
  /** Available start times as `HH:mm:ss`. */
  slots: string[];
  /** Times this client already booked with this lawyer on this date. */
  takenSlots: Set<string>;
  value: string;
  onChange: (time: string) => void;
  className?: string;
}) {
  if (slots.length === 0) {
    return (
      <EmptyState
        icon={CalendarOff}
        title="No times available"
        description="Choose another date to see this lawyer's consultation hours."
      />
    );
  }

  return (
    <div
      role="group"
      aria-label="Available start times"
      className={cn("grid grid-cols-3 gap-2 sm:grid-cols-4", className)}
    >
      {slots.map((slot) => {
        const taken = takenSlots.has(slot);
        const selected = value === slot;

        return (
          <button
            key={slot}
            type="button"
            disabled={taken}
            onClick={() => onChange(slot)}
            aria-pressed={selected}
            title={taken ? "You already have a booking at this time" : undefined}
            className={cn(
              "rounded-lg border px-2 py-2 text-sm transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
              "disabled:cursor-not-allowed disabled:opacity-40",
              selected
                ? "border-primary bg-primary text-primary-foreground"
                : "border-border hover:bg-accent hover:text-accent-foreground",
            )}
          >
            {formatTime(slot)}
          </button>
        );
      })}
    </div>
  );
}
