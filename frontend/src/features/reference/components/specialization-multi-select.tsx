"use client";

import { useMemo } from "react";

import { MultiCombobox } from "@/components/ui/combobox";
import { SPECIALIZATION_OPTIONS } from "@/features/auth/schemas/register-schema";
import { useSpecializations } from "@/features/reference/hooks/use-reference-data";
import { isApiError } from "@/types";

/**
 * Practice areas.
 *
 * Works on NAMES, not ids, and that is deliberate: the registration and profile
 * payloads both send `specializations: string[]`, and this phase must not change
 * either. Swapping the wire format to ids is a later phase, together with the
 * backend switching from resolve-or-create to resolve-or-reject.
 *
 * WHY THE CURATED LIST IS STILL MERGED IN. `GET /api/reference/specializations`
 * returns the contents of the `specializations` table, and that table is
 * populated find-or-create BY REGISTRATION. On a fresh database it is empty - so
 * a picker driven purely by the endpoint would offer a lawyer nothing to choose
 * from, and no lawyer could ever register. Until the backend seeds this
 * vocabulary the two sources are merged, which keeps the form usable and still
 * surfaces anything the server knows about that the constant does not.
 *
 * De-duplication is case-insensitive so "Family law" from the server does not
 * appear beside "Family Law" from the constant.
 */
export function SpecializationMultiSelect({
  value,
  onChange,
  disabled,
  id,
  "aria-invalid": ariaInvalid,
  "aria-describedby": ariaDescribedBy,
}: {
  /** Specialization NAMES, matching the registration payload. */
  value: string[];
  onChange: (names: string[]) => void;
  disabled?: boolean;
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  const { data, isPending, isError, error } = useSpecializations();

  const options = useMemo(() => {
    const byLower = new Map<string, string>();

    // Curated list first, so its casing wins for any duplicate.
    for (const name of SPECIALIZATION_OPTIONS) {
      byLower.set(name.toLowerCase(), name);
    }
    for (const specialization of data ?? []) {
      const key = specialization.name.toLowerCase();
      if (!byLower.has(key)) byLower.set(key, specialization.name);
    }

    return [...byLower.values()].sort((a, b) => a.localeCompare(b));
  }, [data]);

  return (
    <MultiCombobox<string>
      id={id}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      value={value}
      onChange={onChange}
      items={options}
      getKey={(name) => name.toLowerCase()}
      getLabel={(name) => name}
      disabled={disabled}
      /*
       * Never blocks: the curated list is available immediately, so the field is
       * usable while the server list is still in flight.
       */
      loading={isPending && options.length === 0}
      placeholder="Add a practice area…"
      emptyMessage="No matching practice area"
      errorMessage={
        isError && options.length === 0
          ? isApiError(error)
            ? error.message
            : "Could not load practice areas. Please try again."
          : undefined
      }
    />
  );
}
