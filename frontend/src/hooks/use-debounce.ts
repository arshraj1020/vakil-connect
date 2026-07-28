"use client";

import { useEffect, useState } from "react";

/**
 * Delays propagation of a rapidly changing value.
 *
 * Used for the search keyword: without it every keystroke would issue a
 * request and a history entry. The debounced value feeds the query key, so
 * TanStack Query only refetches once typing settles.
 */
export function useDebounce<T>(value: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
