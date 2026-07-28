"use client";

import { SearchX, Users } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { PageHeader } from "@/components/common/page-header";
import { Pagination } from "@/components/common/pagination";
import { Button } from "@/components/ui/button";
import { useDebounce } from "@/hooks/use-debounce";
import { useLawyerSearch } from "@/features/lawyers/hooks/use-lawyer-search";
import {
  buildSearchQuery,
  hasActiveFilters,
  parseSearchParams,
} from "@/features/lawyers/lib/search-params";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import type { LawyerSearchParams } from "@/types";

import { LawyerCard } from "./lawyer-card";
import { LawyerCardGridSkeleton } from "./lawyer-card-skeleton";
import { LawyerFilters } from "./lawyer-filters";
import { SearchResultHeader } from "./search-result-header";

/**
 * Lawyer discovery.
 *
 * Search state lives in the URL, not in component state: results become
 * shareable and bookmarkable, the back button works, and a refresh preserves
 * the search. The query key derives from the same parsed filters, so cache
 * entries and browser history stay aligned.
 *
 * The keyword is the one control with local state - typing must stay responsive
 * - and is debounced before being written to the URL. Both synchronisation
 * effects are guarded by equality checks so they settle instead of looping.
 */
export function LawyerSearchView() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const filters = useMemo(
    () => parseSearchParams(new URLSearchParams(searchParams.toString())),
    [searchParams],
  );

  const [keywordInput, setKeywordInput] = useState(filters.keyword ?? "");
  const debouncedKeyword = useDebounce(keywordInput, 350);

  const applyFilters = useCallback(
    (patch: Partial<LawyerSearchParams>) => {
      const next: LawyerSearchParams = { ...filters, ...patch };
      const query = buildSearchQuery(next);

      // `replace` rather than `push`: typing a query should not fill the
      // history stack with one entry per keystroke. `scroll: false` keeps the
      // viewport steady while filtering.
      router.replace(query ? `${ROUTES.LAWYERS}?${query}` : ROUTES.LAWYERS, {
        scroll: false,
      });
    },
    [filters, router],
  );

  // Debounced keyword -> URL. Any filter change resets to the first page,
  // otherwise a narrower search could land on a page that no longer exists.
  useEffect(() => {
    const current = filters.keyword ?? "";
    if (debouncedKeyword.trim() === current) return;

    applyFilters({ keyword: debouncedKeyword.trim() || undefined, page: 0 });
  }, [applyFilters, debouncedKeyword, filters.keyword]);

  // URL -> input, so browser navigation and "clear filters" update the field.
  useEffect(() => {
    setKeywordInput((current) => {
      const fromUrl = filters.keyword ?? "";
      return current.trim() === fromUrl ? current : fromUrl;
    });
  }, [filters.keyword]);

  const { data, isPending, isError, error, refetch, isPlaceholderData } =
    useLawyerSearch(filters);

  const handleFilterChange = useCallback(
    (patch: Partial<LawyerSearchParams>) => applyFilters({ ...patch, page: 0 }),
    [applyFilters],
  );

  const handleClear = useCallback(() => {
    setKeywordInput("");
    router.replace(ROUTES.LAWYERS, { scroll: false });
  }, [router]);

  const handlePageChange = useCallback(
    (page: number) => {
      applyFilters({ page });
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
    [applyFilters],
  );

  const filtersApplied = hasActiveFilters(filters);
  const lawyers = data?.content ?? [];
  const pageMeta = data?.page;

  return (
    <div className="container space-y-6 py-8">
      <PageHeader
        title="Find a lawyer"
        description="Browse verified legal professionals and book a consultation."
      />

      <LawyerFilters
        filters={filters}
        keyword={keywordInput}
        onKeywordChange={setKeywordInput}
        onFilterChange={handleFilterChange}
        onClear={handleClear}
        hasFilters={filtersApplied}
      />

      {isError ? (
        <ErrorState
          error={error}
          onRetry={() => void refetch()}
          title="Could not load lawyers"
        />
      ) : isPending ? (
        <>
          <SearchResultHeader
            page={0}
            pageSize={DEFAULT_PAGE_SIZE}
            totalElements={0}
            isLoading
          />
          <LawyerCardGridSkeleton />
        </>
      ) : (
        <>
          <SearchResultHeader
            page={pageMeta?.number ?? 0}
            pageSize={pageMeta?.size ?? DEFAULT_PAGE_SIZE}
            totalElements={pageMeta?.totalElements ?? 0}
          />

          {lawyers.length === 0 ? (
            filtersApplied ? (
              <EmptyState
                icon={SearchX}
                title="No lawyers match your filters"
                description="Try widening your search - fewer filters, a different city, or a higher fee range."
                action={
                  <Button variant="outline" size="sm" onClick={handleClear}>
                    Clear filters
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={Users}
                title="No lawyers available yet"
                description="Verified lawyers will appear here as they join the platform."
              />
            )
          ) : (
            <>
              {/*
                While the next page loads, the previous one stays mounted
                (keepPreviousData). Dimming it signals the refresh without
                collapsing the layout.
              */}
              <div
                className={cn(
                  "grid gap-4 transition-opacity sm:grid-cols-2 lg:grid-cols-3",
                  isPlaceholderData && "opacity-60",
                )}
                aria-busy={isPlaceholderData}
              >
                {lawyers.map((lawyer) => (
                  <LawyerCard key={lawyer.id} lawyer={lawyer} />
                ))}
              </div>

              <Pagination
                page={pageMeta?.number ?? 0}
                totalPages={pageMeta?.totalPages ?? 1}
                onPageChange={handlePageChange}
                className="pt-2"
              />
            </>
          )}
        </>
      )}
    </div>
  );
}
