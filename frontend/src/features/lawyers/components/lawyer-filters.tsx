"use client";

import { Search, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSpecializations } from "@/features/reference/hooks/use-reference-data";
import type { LawyerSearchParams } from "@/types";

/**
 * Search and filter controls.
 *
 * The keyword is controlled by the parent (which debounces it before writing to
 * the URL); every other control reports a change immediately, since selecting
 * from a dropdown is already a deliberate action.
 *
 * PRACTICE AREAS COME FROM THE SERVER (Frontend Phase A). They used to come from
 * a hardcoded constant shared with the registration form. Filtering by a name
 * the backend has never heard of simply returns nothing, so a drifted constant
 * showed clients a filter that silently matched no one. The list is cached with
 * `staleTime: Infinity`, so this costs one request per session.
 *
 * That makes this component no longer strictly presentational - it reads one
 * query. The alternative was threading the list down from the page purely to
 * preserve the label, which buys nothing.
 *
 * Radix Select cannot use an empty string as an item value, so "any" is a
 * sentinel translated back to `undefined` at the boundary.
 */

const ANY = "any";

const EXPERIENCE_OPTIONS = [
  { value: "1", label: "1+ years" },
  { value: "3", label: "3+ years" },
  { value: "5", label: "5+ years" },
  { value: "10", label: "10+ years" },
];

const RATING_OPTIONS = [
  { value: "3", label: "3+ stars" },
  { value: "4", label: "4+ stars" },
  { value: "4.5", label: "4.5+ stars" },
];

export function LawyerFilters({
  filters,
  keyword,
  onKeywordChange,
  onFilterChange,
  onClear,
  hasFilters,
}: {
  filters: LawyerSearchParams;
  keyword: string;
  onKeywordChange: (value: string) => void;
  onFilterChange: (patch: Partial<LawyerSearchParams>) => void;
  onClear: () => void;
  hasFilters: boolean;
}) {
  /*
   * A failed reference lookup must not take the search page down with it, so
   * "Any practice area" is always present and the filter degrades to a no-op.
   *
   * Loading and failure are still reported rather than both collapsing into a
   * dropdown that merely looks short. Before Phase A the options came from a
   * constant and could not fail; now they can, and a filter that silently offers
   * nothing is indistinguishable from a catalogue that genuinely has nothing.
   * The placeholders are disabled, so their sentinel values can never reach
   * `onValueChange`.
   */
  const {
    data: specializations = [],
    isPending: specializationsPending,
    isError: specializationsFailed,
  } = useSpecializations();

  const toNumber = (value: string) => (value === ANY ? undefined : Number(value));

  return (
    <Card>
      <CardContent className="space-y-4 p-5">
        {/* Keyword */}
        <div className="space-y-2">
          <Label htmlFor="lawyer-search">Search</Label>
          <div className="relative">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden
            />
            <Input
              id="lawyer-search"
              type="search"
              value={keyword}
              onChange={(event) => onKeywordChange(event.target.value)}
              placeholder="Search by name or keyword"
              className="pl-9"
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* Specialization */}
          <div className="space-y-2">
            <Label htmlFor="filter-specialization">Practice area</Label>
            <Select
              value={filters.specialization ?? ANY}
              onValueChange={(value) =>
                onFilterChange({
                  specialization: value === ANY ? undefined : value,
                })
              }
            >
              <SelectTrigger id="filter-specialization">
                <SelectValue placeholder="Any" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Any practice area</SelectItem>
                {specializationsPending && (
                  <SelectItem value="__loading" disabled>
                    Loading practice areas…
                  </SelectItem>
                )}
                {specializationsFailed && (
                  <SelectItem value="__unavailable" disabled>
                    Practice areas unavailable
                  </SelectItem>
                )}
                {specializations.map((option) => (
                  <SelectItem key={option.id} value={option.name}>
                    {option.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* City */}
          <div className="space-y-2">
            <Label htmlFor="filter-city">City</Label>
            <Input
              id="filter-city"
              value={filters.city ?? ""}
              onChange={(event) =>
                onFilterChange({ city: event.target.value || undefined })
              }
              placeholder="Any city"
            />
          </div>

          {/* Experience */}
          <div className="space-y-2">
            <Label htmlFor="filter-experience">Experience</Label>
            <Select
              value={
                filters.minExperience === undefined
                  ? ANY
                  : String(filters.minExperience)
              }
              onValueChange={(value) =>
                onFilterChange({ minExperience: toNumber(value) })
              }
            >
              <SelectTrigger id="filter-experience">
                <SelectValue placeholder="Any" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Any experience</SelectItem>
                {EXPERIENCE_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Rating */}
          <div className="space-y-2">
            <Label htmlFor="filter-rating">Rating</Label>
            <Select
              value={
                filters.minRating === undefined ? ANY : String(filters.minRating)
              }
              onValueChange={(value) =>
                onFilterChange({ minRating: toNumber(value) })
              }
            >
              <SelectTrigger id="filter-rating">
                <SelectValue placeholder="Any" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Any rating</SelectItem>
                {RATING_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {/* Fee range */}
        <div className="grid gap-4 sm:grid-cols-2 lg:max-w-md">
          <div className="space-y-2">
            <Label htmlFor="filter-min-fee">Min fee</Label>
            <Input
              id="filter-min-fee"
              type="number"
              min={0}
              inputMode="numeric"
              value={filters.minFee ?? ""}
              onChange={(event) =>
                onFilterChange({
                  minFee: event.target.value ? Number(event.target.value) : undefined,
                })
              }
              placeholder="0"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="filter-max-fee">Max fee</Label>
            <Input
              id="filter-max-fee"
              type="number"
              min={0}
              inputMode="numeric"
              value={filters.maxFee ?? ""}
              onChange={(event) =>
                onFilterChange({
                  maxFee: event.target.value ? Number(event.target.value) : undefined,
                })
              }
              placeholder="No limit"
            />
          </div>
        </div>

        {hasFilters ? (
          <div className="flex justify-end">
            <Button variant="ghost" size="sm" onClick={onClear}>
              <X aria-hidden />
              Clear filters
            </Button>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
