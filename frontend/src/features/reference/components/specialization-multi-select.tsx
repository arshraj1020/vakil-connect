"use client";

import { useMemo } from "react";

import { MultiCombobox } from "@/components/ui/combobox";
import { useSpecializations } from "@/features/reference/hooks/use-reference-data";
import { isApiError } from "@/types";

/**
 * Practice areas.
 *
 * Works on NAMES, not ids, and that is deliberate: the registration and profile
 * payloads both send `specializations: string[]`, and neither may change.
 * Swapping the wire format to ids would need a backend change.
 *
 * THE SERVER IS THE ONLY SOURCE (Frontend Phase A). This component used to merge
 * `GET /api/reference/specializations` with a hardcoded constant, because the
 * table was populated find-or-create by registration and was therefore empty on
 * a fresh database - a picker driven purely by the endpoint would have offered
 * the first lawyer nothing to choose from.
 *
 * Phase 2E ended that: V5 seeds the curated vocabulary, so the endpoint is
 * populated before anyone registers. Keeping the merge past that point was
 * actively harmful, because the same phase made the backend resolve-or-REJECT -
 * a name present only in the constant is now a 400 on save, and offering it was
 * offering a value the server will refuse.
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

  /*
   * Still de-duplicated case-insensitively. `specializations.name` is UNIQUE in
   * the database, but that constraint is case-SENSITIVE, so "Family law" and
   * "Family Law" can both exist as rows and would otherwise appear as two
   * indistinguishable options.
   */
  const options = useMemo(() => {
    const byLower = new Map<string, string>();

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
