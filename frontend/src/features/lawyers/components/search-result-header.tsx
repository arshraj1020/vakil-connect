import { formatNumber } from "@/lib/format";

/**
 * Result count and the current range.
 *
 * Converts the zero-based page index to a human range ("Showing 11-20 of 42"),
 * which is the only place that translation happens outside Pagination.
 */
export function SearchResultHeader({
  page,
  pageSize,
  totalElements,
  isLoading,
}: {
  page: number;
  pageSize: number;
  totalElements: number;
  isLoading?: boolean;
}) {
  if (isLoading) {
    return (
      <p className="text-sm text-muted-foreground" aria-live="polite">
        Searching...
      </p>
    );
  }

  if (totalElements === 0) {
    return (
      <p className="text-sm text-muted-foreground" aria-live="polite">
        No lawyers found
      </p>
    );
  }

  const first = page * pageSize + 1;
  const last = Math.min(first + pageSize - 1, totalElements);

  return (
    <p className="text-sm text-muted-foreground" aria-live="polite">
      Showing <span className="font-medium text-foreground">{formatNumber(first)}</span>
      {last > first ? (
        <>
          {"-"}
          <span className="font-medium text-foreground">{formatNumber(last)}</span>
        </>
      ) : null}{" "}
      of <span className="font-medium text-foreground">{formatNumber(totalElements)}</span>{" "}
      {totalElements === 1 ? "lawyer" : "lawyers"}
    </p>
  );
}
