import type { Uuid } from "./common";

/**
 * Reference vocabularies — `GET /api/reference/**`.
 *
 * Public and unauthenticated: registration needs these lists before an account
 * exists. All six endpoints return a bare array, not a page - the collections
 * are bounded by curation, so there is nothing to paginate.
 */

/** A country. Only India is seeded, so this list currently has one entry. */
export interface CountryResponse {
  id: Uuid;
  /** ISO 3166-1 alpha-2, e.g. `IN`. */
  iso2: string;
  name: string;
  /** e.g. `+91`. */
  phoneCode: string;
}

/** A state or union territory. */
export interface StateResponse {
  id: Uuid;
  /** Unique within its country, e.g. `MH`. */
  code: string;
  name: string;
  type: "STATE" | "UNION_TERRITORY";
}

/**
 * A city, always carrying its state.
 *
 * The state travels with every city even on the state-scoped endpoint, where
 * the caller already knows it - several Indian city names repeat across states,
 * so search results are rendered as "Pune, Maharashtra" to disambiguate, and one
 * shape serves both endpoints.
 */
export interface CityResponse {
  id: Uuid;
  name: string;
  stateId: Uuid;
  stateCode: string;
  stateName: string;
}

/**
 * A language.
 *
 * `isoCode` is up to three characters: six of India's scheduled languages have
 * no ISO 639-1 two-letter code, only 639-2/3.
 */
export interface LanguageResponse {
  id: Uuid;
  isoCode: string;
  name: string;
  /** e.g. `मराठी`. Shown alongside the English name in pickers. */
  nativeName: string;
}

/** A practice area. */
export interface SpecializationResponse {
  id: Uuid;
  name: string;
}
