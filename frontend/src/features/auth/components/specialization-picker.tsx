"use client";

import { Check } from "lucide-react";
import { useId } from "react";

import { SPECIALIZATION_OPTIONS } from "@/features/auth/schemas/register-schema";
import { cn } from "@/lib/utils";

/**
 * Multi-select for practice areas.
 *
 * Implemented as native checkboxes inside a <fieldset>, not as toggle buttons.
 * Per the WAI-ARIA Authoring Practices, choosing zero or more items from a list
 * IS the checkbox pattern; `aria-pressed` toggle buttons describe actions with
 * an on/off state (bold, mute), which is a different thing.
 *
 * Using the native control means the semantics and keyboard behaviour come for
 * free and correctly:
 *  - screen readers announce "Specializations, group ... Family Law, checkbox,
 *    not checked, 3 of 10",
 *  - Tab moves between options and Space toggles, with no key handling here,
 *  - <legend> gives the group its accessible name. A <label htmlFor> could not:
 *    htmlFor only binds to labelable elements, so pointing it at a grouping
 *    element produced a dangling association and no accessible name.
 *
 * The validation message is linked with `aria-describedby` on the fieldset,
 * which supports it. `aria-invalid` is deliberately absent: fieldset does not
 * support it, and flagging each checkbox individually would be untrue - no
 * single option is invalid, the selection as a whole is incomplete.
 *
 * The component owns its label and error markup (rather than being wrapped in
 * FormField) because a group needs <legend>, not <label>.
 */
export function SpecializationPicker({
  value,
  onChange,
  error,
  hint,
  required = false,
  legend = "Specializations",
}: {
  value: string[];
  onChange: (next: string[]) => void;
  error?: string;
  hint?: string;
  required?: boolean;
  legend?: string;
}) {
  const descriptionId = useId();
  const hasError = Boolean(error);

  const toggle = (option: string) => {
    onChange(
      value.includes(option)
        ? value.filter((item) => item !== option)
        : [...value, option],
    );
  };

  return (
    <fieldset
      className="space-y-2"
      aria-describedby={hasError || hint ? descriptionId : undefined}
    >
      <legend className="mb-2 text-sm font-medium leading-none">
        {legend}
        {required ? (
          <span className="ml-0.5 text-destructive" aria-hidden>
            *
          </span>
        ) : null}
      </legend>

      <div
        className={cn(
          "flex flex-wrap gap-2 rounded-lg border p-3",
          hasError ? "border-destructive" : "border-input",
        )}
      >
        {SPECIALIZATION_OPTIONS.map((option) => {
          const selected = value.includes(option);

          return (
            <label
              key={option}
              className={cn(
                "inline-flex cursor-pointer items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium transition-colors",
                "has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring has-[:focus-visible]:ring-offset-2 has-[:focus-visible]:ring-offset-background",
                selected
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-accent hover:text-accent-foreground",
              )}
            >
              <input
                type="checkbox"
                className="sr-only"
                checked={selected}
                onChange={() => toggle(option)}
              />
              {selected ? <Check className="size-3" aria-hidden /> : null}
              {option}
            </label>
          );
        })}
      </div>

      {hasError ? (
        <p id={descriptionId} className="text-xs text-destructive" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={descriptionId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </fieldset>
  );
}
