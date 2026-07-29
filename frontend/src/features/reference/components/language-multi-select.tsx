"use client";

import { MultiCombobox } from "@/components/ui/combobox";
import { useLanguages } from "@/features/reference/hooks/use-reference-data";
import { isApiError, type LanguageResponse } from "@/types";

/**
 * Languages a person speaks.
 *
 * Works on `LanguageResponse` objects rather than codes or names: the caller
 * decides what to send on the wire, and a component that returns whole records
 * can serve a payload of ids as easily as one of codes.
 *
 * The native name is the secondary line, and it is not decoration - a Marathi
 * speaker scanning this list should see "मराठी". On an India-first platform that
 * is the difference between a usable control and a list of English exonyms.
 */
export function LanguageMultiSelect({
  value,
  onChange,
  disabled,
  id,
  "aria-invalid": ariaInvalid,
  "aria-describedby": ariaDescribedBy,
}: {
  value: LanguageResponse[];
  onChange: (languages: LanguageResponse[]) => void;
  disabled?: boolean;
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  const { data, isPending, isError, error } = useLanguages();

  return (
    <MultiCombobox<LanguageResponse>
      id={id}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      value={value}
      onChange={onChange}
      items={data ?? []}
      getKey={(language) => language.id}
      getLabel={(language) => language.name}
      getDescription={(language) => language.nativeName}
      disabled={disabled}
      loading={isPending}
      placeholder="Add a language…"
      emptyMessage="No matching language"
      errorMessage={
        isError
          ? isApiError(error)
            ? error.message
            : "Could not load languages. Please try again."
          : undefined
      }
    />
  );
}
