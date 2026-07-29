"use client";

import { Combobox } from "@/components/ui/combobox";
import { useCountries } from "@/features/reference/hooks/use-reference-data";
import { isApiError, type CountryResponse } from "@/types";

/**
 * Country picker.
 *
 * Only India is seeded, so this currently offers one option. It exists because
 * the backend models countries properly and a caller should not have to
 * hardcode "IN" - and because a control that appears when a second country is
 * added is cheaper than retrofitting one.
 *
 * Callers rendering a single-country form should hide this rather than show a
 * dropdown with one entry; `useCountries` gives them the count to decide.
 */
export function CountryCombobox({
  value,
  onChange,
  disabled,
  id,
  "aria-invalid": ariaInvalid,
  "aria-describedby": ariaDescribedBy,
}: {
  value: CountryResponse | null;
  onChange: (country: CountryResponse | null) => void;
  disabled?: boolean;
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  const { data, isPending, isError, error } = useCountries();

  return (
    <Combobox<CountryResponse>
      id={id}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      value={value}
      onChange={onChange}
      items={data ?? []}
      getKey={(country) => country.id}
      getLabel={(country) => country.name}
      getDescription={(country) => country.phoneCode}
      disabled={disabled}
      loading={isPending}
      placeholder="Select a country"
      emptyMessage="No matching country"
      errorMessage={
        isError
          ? isApiError(error)
            ? error.message
            : "Could not load countries. Please try again."
          : undefined
      }
    />
  );
}
