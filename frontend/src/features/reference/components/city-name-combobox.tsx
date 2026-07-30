"use client";

import { CityCombobox } from "@/features/reference/components/city-combobox";
import type { CityResponse } from "@/types";

/**
 * `CityCombobox` for forms whose field holds a city NAME rather than a city.
 *
 * Both places that ask for a city - registration and the lawyer profile - keep a
 * plain string in form state, because that is what the API takes:
 * `CreateLawyerProfileRequest.city` and `UpdateLawyerProfileRequest.city` are
 * both `String`. Carrying the id through form state would change the payload,
 * which is out of scope for this phase and would need a backend change to be
 * worth anything.
 *
 * This adapter exists so that reconciliation lives in ONE place. Registration
 * previously did it inline; the profile form needed the same thing, and a second
 * copy is how the two quietly drift apart.
 *
 * THE SYNTHETIC SELECTION. `CityCombobox` is object-valued, so a bare name has
 * to be widened into a `CityResponse` for display. Only `name` is real - the id
 * and state fields are placeholders, and nothing downstream reads them: the
 * combobox uses `id` solely as a React key for the selected item, and the state
 * fields only decorate options in the dropdown, which come from the server with
 * their real values. When the user picks something, the real object arrives and
 * we take `name` off it.
 *
 * This is also what lets an existing profile round-trip. A lawyer whose stored
 * city predates the reference data still sees it in the field and can save
 * without touching it; they only have to choose a curated city if they decide to
 * change it.
 */
export function CityNameCombobox({
  value,
  onChange,
  disabled,
  id,
  "aria-invalid": ariaInvalid,
  "aria-describedby": ariaDescribedBy,
}: {
  /** The city NAME, exactly as it is sent to the API. */
  value: string;
  onChange: (cityName: string) => void;
  disabled?: boolean;
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  const selected: CityResponse | null = value
    ? {
        id: value,
        name: value,
        stateId: "",
        stateCode: "",
        stateName: "",
      }
    : null;

  return (
    <CityCombobox
      id={id}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      disabled={disabled}
      value={selected}
      /*
       * Clearing yields "" rather than null: the field is a required non-empty
       * string in both schemas, and null would be a second empty value for the
       * same state.
       */
      onChange={(city) => onChange(city?.name ?? "")}
    />
  );
}
