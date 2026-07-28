"use client";

import {
  STATUS_FILTERS,
  type StatusFilter,
} from "@/features/lawyer-appointments/lib/filters";
import { cn } from "@/lib/utils";

/**
 * Status filter chips.
 *
 * A radio group rather than tabs: these select which subset of one list is
 * shown, they do not switch between separate panels. `aria-checked` on real
 * buttons communicates the single-selection semantics, and arrow-key roving is
 * unnecessary because each chip is individually tabbable and labelled.
 */
export function AppointmentFilters({
  value,
  counts,
  onChange,
}: {
  value: StatusFilter;
  counts: Record<StatusFilter, number>;
  onChange: (filter: StatusFilter) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label="Filter appointments by status"
      className="flex flex-wrap gap-2"
    >
      {STATUS_FILTERS.map((filter) => {
        const selected = value === filter.value;
        const count = counts[filter.value];

        return (
          <button
            key={filter.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(filter.value)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
              selected
                ? "border-primary bg-primary text-primary-foreground"
                : "border-border text-muted-foreground hover:bg-accent hover:text-accent-foreground",
            )}
          >
            {filter.label}
            <span className={cn("text-xs", selected ? "opacity-80" : "opacity-60")}>
              {count}
            </span>
          </button>
        );
      })}
    </div>
  );
}
