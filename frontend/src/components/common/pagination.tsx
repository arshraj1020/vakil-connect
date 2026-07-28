"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Page navigation for any paged endpoint.
 *
 * Pages are ZERO-BASED to match Spring Data, and converted to one-based only
 * for display - keeping the off-by-one in one place rather than at every call
 * site.
 *
 * The number window keeps the control a fixed width regardless of total pages,
 * so the layout does not shift as results change.
 */
export function Pagination({
  page,
  totalPages,
  onPageChange,
  className,
}: {
  /** Zero-based. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}) {
  if (totalPages <= 1) return null;

  const pages = pageWindow(page, totalPages);

  return (
    <nav
      className={cn("flex items-center justify-center gap-1", className)}
      aria-label="Pagination"
    >
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        aria-label="Previous page"
      >
        <ChevronLeft aria-hidden />
        <span className="hidden sm:inline">Previous</span>
      </Button>

      <ul className="flex items-center gap-1">
        {pages.map((entry, index) =>
          entry === "gap" ? (
            <li
              key={`gap-${index}`}
              className="px-2 text-sm text-muted-foreground"
              aria-hidden
            >
              &hellip;
            </li>
          ) : (
            <li key={entry}>
              <Button
                variant={entry === page ? "default" : "ghost"}
                size="sm"
                onClick={() => onPageChange(entry)}
                aria-label={`Page ${entry + 1}`}
                aria-current={entry === page ? "page" : undefined}
                className="min-w-9"
              >
                {entry + 1}
              </Button>
            </li>
          ),
        )}
      </ul>

      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="Next page"
      >
        <span className="hidden sm:inline">Next</span>
        <ChevronRight aria-hidden />
      </Button>
    </nav>
  );
}

/** First, last, and a window around the current page, with gaps marked. */
function pageWindow(page: number, totalPages: number): Array<number | "gap"> {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }

  const entries = new Set<number>([0, totalPages - 1, page]);
  if (page - 1 > 0) entries.add(page - 1);
  if (page + 1 < totalPages - 1) entries.add(page + 1);

  const sorted = [...entries].sort((a, b) => a - b);
  const result: Array<number | "gap"> = [];

  sorted.forEach((value, index) => {
    const previous = sorted[index - 1];
    if (previous !== undefined && value - previous > 1) result.push("gap");
    result.push(value);
  });

  return result;
}
