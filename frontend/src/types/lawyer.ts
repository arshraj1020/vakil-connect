import type { PageParams, ShortTime, Uuid } from "./common";

/**
 * Lawyer profile, discovery and availability contracts.
 *
 * Availability lives here rather than in its own module because it belongs to
 * the lawyer package on the backend (`lawyer/entity/Availability`,
 * `lawyer/dto/AvailabilityResponse`).
 */

/* ------------------------------------------------------------------ profile */

/**
 * Professional details required to create a lawyer profile.
 *
 * Sent nested inside `RegisterRequest.lawyerProfile` - the backend creates the
 * `User` and `Lawyer` atomically in a single transaction, so every field here
 * is mandatory at signup. `barCouncilNumber` is UNIQUE and immutable once set.
 */
export interface CreateLawyerProfileRequest {
  barCouncilNumber: string;
  /** Minimum 0. */
  experienceYears: number;
  /** Max 2000 characters. */
  bio: string;
  /** Must be greater than 0. */
  consultationFee: number;
  city: string;
  officeAddress: string;
  /** At least one required. Resolved find-or-create by name on the backend. */
  specializations: string[];
}

/**
 * Editable subset of a lawyer profile.
 *
 * `barCouncilNumber` is absent by design: it is a legal identifier and the
 * backend does not expose it for update. `verified`, `rating` and
 * `totalReviews` are system-managed and likewise not editable.
 *
 * Note: `specializations` REPLACES the existing set, it is not additive.
 */
export interface UpdateLawyerProfileRequest {
  experienceYears: number;
  bio: string;
  consultationFee: number;
  city: string;
  officeAddress: string;
  specializations: string[];
}

/** Full lawyer profile. Returned by the public detail endpoint and after updates. */
export interface LawyerProfileResponse {
  id: Uuid;
  fullName: string;
  email: string;
  /** Optional at registration, so nullable. */
  phoneNumber: string | null;

  barCouncilNumber: string;
  experienceYears: number;
  bio: string;
  consultationFee: number;
  city: string;
  officeAddress: string;

  /** Admin-verified. Only verified lawyers appear in search and accept bookings. */
  verified: boolean;
  /** 0.0 until the first review. Rounded to 2 decimals by the backend. */
  rating: number;
  totalReviews: number;
  specializations: string[];
}

/** Condensed lawyer for search results and admin lists. */
export interface LawyerSummaryResponse {
  id: Uuid;
  fullName: string;
  city: string;
  experienceYears: number;
  consultationFee: number;
  rating: number;
  totalReviews: number;
  specializations: string[];
}

/* ------------------------------------------------------------------- search */

/**
 * Filters for `GET /api/lawyers` (public).
 *
 * Every filter is optional; omitted filters are ignored. The endpoint only ever
 * returns *verified* lawyers - there is no parameter to include unverified
 * ones (admins use the pending-verification endpoint instead).
 */
export interface LawyerSearchParams extends PageParams {
  /** Matched case-insensitively against full name and bio. */
  keyword?: string;
  /** Exact, case-insensitive specialization name. */
  specialization?: string;
  /** Exact, case-insensitive city. */
  city?: string;
  minFee?: number;
  maxFee?: number;
  minExperience?: number;
  /** 0-5. */
  minRating?: number;
}

/* ------------------------------------------------------------- availability */

/** Java `DayOfWeek`, serialized as its enum name. */
export type DayOfWeek =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

/**
 * A recurring weekly consultation window.
 *
 * Times use `HH:mm` (not `HH:mm:ss`) because the DTO declares
 * `@JsonFormat(pattern = "HH:mm")`.
 */
export interface CreateAvailabilityRequest {
  dayOfWeek: DayOfWeek;
  /** `HH:mm`. Must be strictly before `endTime`. */
  startTime: ShortTime;
  /** `HH:mm`. */
  endTime: ShortTime;
}

/**
 * A lawyer's availability window.
 *
 * Availability is a RECURRING WEEKLY pattern, not a set of specific dates: to
 * decide whether a chosen date is bookable, map that date to its weekday and
 * look for a matching window.
 *
 * Matching is `startTime <= time < endTime` - the opening time is INCLUSIVE and
 * the closing time is EXCLUSIVE (13:00 is not bookable in a 10:00-13:00 window).
 */
export interface AvailabilityResponse {
  id: Uuid;
  dayOfWeek: DayOfWeek;
  /** `HH:mm`. */
  startTime: ShortTime;
  /** `HH:mm`. */
  endTime: ShortTime;
  available: boolean;
}
