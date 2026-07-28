"use client";

import { Pagination } from "@/components/common/pagination";
import { formatNumber } from "@/lib/format";

/**
 * Page navigation plus a plain-language position summary.
 *
 * The control itself is the shared `Pagination` component; this only adds the
 * "Showing 1-10 of 24" line, which `Pagination` deliberately omits because most
 * callers page through lists whose count is already visible nearby.
 *
 * Lives in `components/common` rather than inside a feature because both admin
 * queues need it. It began in the verification feature and was promoted here
 * when user management needed the same thing - one implementation, two callers,
 * with only the trailing noun differing.
 *
 * `aria-live="polite"` announces the new range after a page change, since the
 * rows above swap without moving focus.
 */
export function PaginationControls({
  page,
  size,
  totalPages,
  totalElements,
  onPageChange,
  itemLabel = "results",
}: {
  /** Zero-based, matching the API. */
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  /** Trailing noun, e.g. "users" or "awaiting verification". */
  itemLabel?: string;
}) {
  if (totalElements === 0) return null;

  const first = page * size + 1;
  const last = Math.min(first + size - 1, totalElements);

  return (
    <div className="flex flex-col items-center gap-3">
      <Pagination
        page={page}
        totalPages={totalPages}
        onPageChange={onPageChange}
      />

      <p aria-live="polite" className="text-xs text-muted-foreground">
        Showing {formatNumber(first)}–{formatNumber(last)} of{" "}
        {formatNumber(totalElements)} {itemLabel}
      </p>
    </div>
  );
}
