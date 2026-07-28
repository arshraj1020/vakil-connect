import api from "@/lib/axios";
import type {
  AvailabilityResponse,
  LawyerProfileResponse,
  LawyerSearchParams,
  LawyerSummaryResponse,
  Paged,
  PageParams,
  ReviewResponse,
} from "@/types";

/**
 * Public lawyer discovery endpoints.
 *
 * All four are unauthenticated (`GET /api/lawyers/**` is permitted for
 * anonymous users), so the browse experience works before sign-in.
 */

const ENDPOINTS = {
  search: "/api/lawyers",
  detail: (lawyerId: string) => `/api/lawyers/${lawyerId}`,
  reviews: (lawyerId: string) => `/api/lawyers/${lawyerId}/reviews`,
  availability: (lawyerId: string) => `/api/lawyers/${lawyerId}/availability`,
} as const;

/**
 * Paged, filtered lawyer search.
 *
 * Only VERIFIED lawyers are returned - the backend hardcodes that predicate and
 * exposes no parameter to include unverified profiles.
 *
 * Undefined filters are dropped rather than sent empty: the backend treats
 * blanks as absent anyway, and omitting them keeps the URL and the query cache
 * key clean.
 */
export async function searchLawyers(
  params: LawyerSearchParams,
): Promise<Paged<LawyerSummaryResponse>> {
  const { data } = await api.get<Paged<LawyerSummaryResponse>>(ENDPOINTS.search, {
    params: stripEmpty(params),
  });
  return data;
}

/** Full public profile. Returns 404 for an unknown id. */
export async function getLawyerProfile(
  lawyerId: string,
): Promise<LawyerProfileResponse> {
  const { data } = await api.get<LawyerProfileResponse>(ENDPOINTS.detail(lawyerId));
  return data;
}

/** Paged reviews, newest first. */
export async function getLawyerReviews(
  lawyerId: string,
  params: PageParams,
): Promise<Paged<ReviewResponse>> {
  const { data } = await api.get<Paged<ReviewResponse>>(
    ENDPOINTS.reviews(lawyerId),
    { params: stripEmpty(params) },
  );
  return data;
}

/**
 * The lawyer's recurring weekly availability.
 *
 * Returns a plain array, not a page. Windows are weekly patterns rather than
 * specific dates, so a date is bookable when its WEEKDAY matches a window.
 */
export async function getLawyerAvailability(
  lawyerId: string,
): Promise<AvailabilityResponse[]> {
  const { data } = await api.get<AvailabilityResponse[]>(
    ENDPOINTS.availability(lawyerId),
  );
  return data;
}

/**
 * Removes undefined, null and empty-string values before they reach the query
 * string.
 *
 * Constrained to `object` rather than `Record<string, unknown>`: TypeScript
 * interfaces have no implicit index signature, so the DTO interfaces would not
 * satisfy the stricter constraint.
 */
function stripEmpty<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== "",
    ),
  ) as Partial<T>;
}

export const lawyerService = {
  searchLawyers,
  getLawyerProfile,
  getLawyerReviews,
  getLawyerAvailability,
} as const;
