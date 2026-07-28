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
import { SPECIALIZATION_OPTIONS } from "@/features/auth/schemas/register-schema";
import type { LawyerSearchParams } from "@/types";

/**
 * Search and filter controls.
 *
 * Presentational: it owns no state and issues no requests. The keyword is
 * controlled by the parent (which debounces it before writing to the URL);
 * every other control reports a change immediately, since selecting from a
 * dropdown is already a deliberate action.
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
                {SPECIALIZATION_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option}
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
