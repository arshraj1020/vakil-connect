"use client";

import { useQuery } from "@tanstack/react-query";

import { useDebounce } from "@/hooks/use-debounce";
import { queryKeys } from "@/lib/query-keys";
import { referenceService } from "@/services/reference-service";

/**
 * Reference vocabularies as TanStack Query hooks.
 *
 * Server state, so it belongs here and not in Zustand - the store stays the
 * session container it was designed to be.
 *
 * CACHING. These lists change on the order of once a quarter and the backend
 * already serves them with a 24h `Cache-Control` and an ETag. `staleTime:
 * Infinity` means the client never refetches them within a session: no refetch
 * on window focus, on remount, or on reconnect. A page that mounts five pickers
 * issues at most five requests for the lifetime of the tab, and repeat visits
 * are served from the query cache with no network at all.
 *
 * `gcTime` is long rather than infinite so a tab left open for days eventually
 * releases the memory if nothing is observing it.
 */

/** These are effectively static within a session. */
const STATIC_LIST = {
  staleTime: Number.POSITIVE_INFINITY,
  gcTime: 1000 * 60 * 60 * 24,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
} as const;

/** Below this, a search is too broad to be useful and is not sent. */
export const MIN_CITY_QUERY_LENGTH = 2;

/** Matches the rhythm of typing without issuing a request per keystroke. */
export const CITY_SEARCH_DEBOUNCE_MS = 300;

export function useCountries() {
  return useQuery({
    queryKey: queryKeys.reference.countries(),
    queryFn: referenceService.getCountries,
    ...STATIC_LIST,
  });
}

/**
 * States of a country.
 *
 * `countryIso2` defaults to India, the only seeded country. The parameter exists
 * so a second country needs no change here.
 */
export function useStates(countryIso2 = "IN") {
  return useQuery({
    queryKey: queryKeys.reference.states(countryIso2),
    queryFn: () => referenceService.getStates(countryIso2),
    enabled: countryIso2.length > 0,
    ...STATIC_LIST,
  });
}

/**
 * Cities of one state - the dependent dropdown.
 *
 * Disabled until a state is chosen, so selecting one is what triggers the fetch
 * rather than mounting the component.
 */
export function useCities(stateId: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.reference.cities(stateId ?? ""),
    queryFn: () => referenceService.getCities(stateId ?? ""),
    enabled: Boolean(stateId),
    ...STATIC_LIST,
  });
}

/**
 * Typeahead over cities, debounced and length-gated.
 *
 * Two guards, doing different jobs:
 *
 *   - the DEBOUNCE stops a request per keystroke; the query key uses the settled
 *     term, so a user who pauses and resumes on the same prefix gets a cache hit
 *     rather than a refetch
 *   - the MINIMUM LENGTH stops one-character searches, which match most of the
 *     dataset and tell the user nothing
 *
 * `isTyping` is returned so the caller can distinguish "waiting for you to stop
 * typing" from "waiting for the server" - showing a spinner during the debounce
 * window makes a responsive field feel slow.
 */
export function useCitySearch(query: string) {
  const debounced = useDebounce(query.trim(), CITY_SEARCH_DEBOUNCE_MS);
  const enabled = debounced.length >= MIN_CITY_QUERY_LENGTH;

  const result = useQuery({
    queryKey: queryKeys.reference.citySearch(debounced),
    queryFn: () => referenceService.searchCities(debounced),
    enabled,
    // A search result is not static: an admin may add a city. Short but non-zero,
    // so backspacing through a word does not re-request every prefix.
    staleTime: 1000 * 60 * 5,
    gcTime: 1000 * 60 * 30,
    refetchOnWindowFocus: false,
  });

  return {
    ...result,
    /** True while the debounce has not caught up with what the user typed. */
    isTyping: query.trim() !== debounced,
    /** True when the term is too short to search. */
    isQueryTooShort: query.trim().length > 0 && !enabled,
    enabled,
  };
}

export function useLanguages() {
  return useQuery({
    queryKey: queryKeys.reference.languages(),
    queryFn: referenceService.getLanguages,
    ...STATIC_LIST,
  });
}

export function useSpecializations() {
  return useQuery({
    queryKey: queryKeys.reference.specializations(),
    queryFn: referenceService.getSpecializations,
    ...STATIC_LIST,
  });
}
