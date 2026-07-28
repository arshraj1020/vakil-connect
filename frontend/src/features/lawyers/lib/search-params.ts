import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE } from "@/lib/constants";
import type { LawyerSearchParams } from "@/types";

/**
 * Translation between the URL query string and `LawyerSearchParams`.
 *
 * Search state lives in the URL rather than component state so results are
 * shareable and bookmarkable, the back button works, and a refresh preserves
 * the search. It also means the TanStack query key derives from the URL, so
 * cache entries and history line up without extra bookkeeping.
 *
 * Everything here tolerates junk: a hand-edited `?page=banana` should fall back
 * to a sane default rather than propagate NaN into a request.
 */

export const SEARCH_PARAM_KEYS = {
  keyword: "keyword",
  specialization: "specialization",
  city: "city",
  minFee: "minFee",
  maxFee: "maxFee",
  minExperience: "minExperience",
  minRating: "minRating",
  page: "page",
} as const;

function readNumber(
  params: URLSearchParams,
  key: string,
  { min, max }: { min?: number; max?: number } = {},
): number | undefined {
  const raw = params.get(key);
  if (raw === null || raw.trim() === "") return undefined;

  const parsed = Number(raw);
  if (!Number.isFinite(parsed)) return undefined;
  if (min !== undefined && parsed < min) return undefined;
  if (max !== undefined && parsed > max) return undefined;

  return parsed;
}

function readText(params: URLSearchParams, key: string): string | undefined {
  const raw = params.get(key)?.trim();
  return raw ? raw : undefined;
}

/** Builds the request parameters for a given URL query string. */
export function parseSearchParams(
  params: URLSearchParams,
): LawyerSearchParams {
  return {
    keyword: readText(params, SEARCH_PARAM_KEYS.keyword),
    specialization: readText(params, SEARCH_PARAM_KEYS.specialization),
    city: readText(params, SEARCH_PARAM_KEYS.city),
    minFee: readNumber(params, SEARCH_PARAM_KEYS.minFee, { min: 0 }),
    maxFee: readNumber(params, SEARCH_PARAM_KEYS.maxFee, { min: 0 }),
    minExperience: readNumber(params, SEARCH_PARAM_KEYS.minExperience, {
      min: 0,
      max: 80,
    }),
    minRating: readNumber(params, SEARCH_PARAM_KEYS.minRating, { min: 0, max: 5 }),
    page: readNumber(params, SEARCH_PARAM_KEYS.page, { min: 0 }) ?? 0,
    size: DEFAULT_PAGE_SIZE,
  };
}

/**
 * Serialises filters back to a query string.
 *
 * Empty values are omitted so the URL stays readable, and `page=0` is dropped
 * because it is the default - `/lawyers?city=Mumbai` rather than
 * `/lawyers?city=Mumbai&page=0`.
 */
export function buildSearchQuery(filters: LawyerSearchParams): string {
  const params = new URLSearchParams();

  const setText = (key: string, value: string | undefined) => {
    if (value && value.trim()) params.set(key, value.trim());
  };
  const setNumber = (key: string, value: number | undefined) => {
    if (value !== undefined && Number.isFinite(value)) params.set(key, String(value));
  };

  setText(SEARCH_PARAM_KEYS.keyword, filters.keyword);
  setText(SEARCH_PARAM_KEYS.specialization, filters.specialization);
  setText(SEARCH_PARAM_KEYS.city, filters.city);
  setNumber(SEARCH_PARAM_KEYS.minFee, filters.minFee);
  setNumber(SEARCH_PARAM_KEYS.maxFee, filters.maxFee);
  setNumber(SEARCH_PARAM_KEYS.minExperience, filters.minExperience);
  setNumber(SEARCH_PARAM_KEYS.minRating, filters.minRating);

  if (filters.page && filters.page > 0) {
    params.set(SEARCH_PARAM_KEYS.page, String(filters.page));
  }

  return params.toString();
}

/** True when any filter beyond paging is applied - drives the "clear" affordance. */
export function hasActiveFilters(filters: LawyerSearchParams): boolean {
  return Boolean(
    filters.keyword ||
      filters.specialization ||
      filters.city ||
      filters.minFee !== undefined ||
      filters.maxFee !== undefined ||
      filters.minExperience !== undefined ||
      filters.minRating !== undefined,
  );
}

/** Clamps a page size to something the API should never be asked to exceed. */
export function clampPageSize(size: number): number {
  return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
}
