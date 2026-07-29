"use client";

import { Combobox } from "@/components/ui/combobox";
import {
  MIN_CITY_QUERY_LENGTH,
  useCitySearch,
} from "@/features/reference/hooks/use-reference-data";
import { isApiError, type CityResponse } from "@/types";
import { useState } from "react";

/**
 * City picker backed by the server's typeahead.
 *
 * Search-first rather than state-then-city: someone who knows their city should
 * not have to locate Maharashtra in a list of 36 before typing "Pune". The
 * endpoint also resolves historical names, so "Bombay" and "Bangalore" find
 * Mumbai and Bengaluru - which a dropdown of current names alone would not.
 *
 * Every result shows its state, because several Indian city names repeat across
 * states and the name alone would be ambiguous.
 *
 * Debouncing and the minimum query length live in `useCitySearch`, not here, so
 * every consumer of the hook gets the same behaviour.
 */
export function CityCombobox({
  value,
  onChange,
  disabled,
  id,
  "aria-invalid": ariaInvalid,
  "aria-describedby": ariaDescribedBy,
}: {
  value: CityResponse | null;
  onChange: (city: CityResponse | null) => void;
  disabled?: boolean;
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  const [query, setQuery] = useState("");
  const { data, isFetching, isError, error, isTyping, isQueryTooShort } =
    useCitySearch(query);

  return (
    <Combobox<CityResponse>
      id={id}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      value={value}
      onChange={onChange}
      items={data ?? []}
      onQueryChange={setQuery}
      getKey={(city) => city.id}
      getLabel={(city) => city.name}
      getDescription={(city) => city.stateName}
      disabled={disabled}
      placeholder="Start typing a city…"
      /*
       * Only a request in flight counts as loading. Showing a spinner during the
       * debounce window makes a field that is working perfectly feel slow.
       */
      loading={isFetching && !isTyping}
      hintMessage={
        query.trim().length === 0
          ? `Type at least ${MIN_CITY_QUERY_LENGTH} characters to search`
          : isQueryTooShort
            ? `Keep typing — ${MIN_CITY_QUERY_LENGTH} characters minimum`
            : undefined
      }
      emptyMessage="No matching city. Try the district headquarters."
      errorMessage={
        isError
          ? isApiError(error)
            ? error.message
            : "Could not load cities. Please try again."
          : undefined
      }
    />
  );
}
