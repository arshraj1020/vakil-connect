"use client";

import { Combobox } from "@/components/ui/combobox";
import { useStates } from "@/features/reference/hooks/use-reference-data";
import { isApiError, type StateResponse } from "@/types";

/**
 * State / union territory picker.
 *
 * A combobox rather than a plain select: 36 entries is past the point where
 * scanning beats typing, and it keeps the keyboard model identical to the city
 * field beside it.
 *
 * The type is shown as the secondary line so a union territory is not silently
 * presented as a state - a distinction that is factually wrong to collapse on an
 * India-first platform.
 */
export function StateCombobox({
  value,
  onChange,
  countryIso2 = "IN",
  disabled,
  id,
  "aria-invalid": ariaInvalid,
  "aria-describedby": ariaDescribedBy,
}: {
  value: StateResponse | null;
  onChange: (state: StateResponse | null) => void;
  countryIso2?: string;
  disabled?: boolean;
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  const { data, isPending, isError, error } = useStates(countryIso2);

  return (
    <Combobox<StateResponse>
      id={id}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      value={value}
      onChange={onChange}
      items={data ?? []}
      getKey={(state) => state.id}
      getLabel={(state) => state.name}
      getDescription={(state) =>
        state.type === "UNION_TERRITORY" ? "Union territory" : undefined
      }
      disabled={disabled}
      loading={isPending}
      placeholder="Select a state"
      emptyMessage="No matching state"
      errorMessage={
        isError
          ? isApiError(error)
            ? error.message
            : "Could not load states. Please try again."
          : undefined
      }
    />
  );
}
