import api from "@/lib/axios";
import type {
  CityResponse,
  CountryResponse,
  LanguageResponse,
  SpecializationResponse,
  StateResponse,
} from "@/types";

/**
 * Public reference vocabularies.
 *
 * Unauthenticated by design - registration needs the city and specialization
 * lists before an account exists. The Axios request interceptor still attaches a
 * token when one is present, which is harmless: the backend permits these
 * endpoints anonymously and ignores the header.
 *
 * Every endpoint returns a bare array. The collections are bounded by curation
 * (1 country, 36 states, ~200 cities, 23 languages) so none is paginated; the
 * one unbounded surface, city search, is capped by `limit` instead.
 */

const ENDPOINTS = {
  countries: "/api/reference/countries",
  states: "/api/reference/states",
  cities: "/api/reference/cities",
  citySearch: "/api/reference/cities/search",
  languages: "/api/reference/languages",
  specializations: "/api/reference/specializations",
} as const;

export async function getCountries(): Promise<CountryResponse[]> {
  const { data } = await api.get<CountryResponse[]>(ENDPOINTS.countries);
  return data;
}

/**
 * States of one country.
 *
 * An unknown code answers 400, not an empty list - the backend treats an
 * unresolvable reference identifier as a malformed request rather than a
 * missing resource.
 */
export async function getStates(countryIso2: string): Promise<StateResponse[]> {
  const { data } = await api.get<StateResponse[]>(ENDPOINTS.states, {
    params: { countryIso2 },
  });
  return data;
}

/** Cities of one state - the dependent dropdown. Unknown `stateId` answers 400. */
export async function getCities(stateId: string): Promise<CityResponse[]> {
  const { data } = await api.get<CityResponse[]>(ENDPOINTS.cities, {
    params: { stateId },
  });
  return data;
}

/**
 * Typeahead across city names AND historical aliases.
 *
 * The alias coverage is why this exists separately from the dropdown:
 * "Bangalore", "Bombay" and "Gurgaon" are still what people type, and a picker
 * that cannot resolve them is worse than the free text it replaces.
 *
 * A blank query returns an empty list rather than the whole dataset, so callers
 * need not special-case it - though they should still avoid the round trip.
 */
export async function searchCities(
  query: string,
  limit = 10,
): Promise<CityResponse[]> {
  const { data } = await api.get<CityResponse[]>(ENDPOINTS.citySearch, {
    params: { q: query, limit },
  });
  return data;
}

export async function getLanguages(): Promise<LanguageResponse[]> {
  const { data } = await api.get<LanguageResponse[]>(ENDPOINTS.languages);
  return data;
}

export async function getSpecializations(): Promise<SpecializationResponse[]> {
  const { data } = await api.get<SpecializationResponse[]>(
    ENDPOINTS.specializations,
  );
  return data;
}

export const referenceService = {
  getCountries,
  getStates,
  getCities,
  searchCities,
  getLanguages,
  getSpecializations,
} as const;
