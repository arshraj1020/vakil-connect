import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { isApiError } from "@/types";

/**
 * Routes a backend failure to the right place in a form.
 *
 * The API returns two distinct kinds of failure and they need different
 * treatment:
 *
 *  - `fieldErrors` (400) belongs on the offending input, where the user is
 *    looking. Toasting "Validation failed" would make them hunt for it.
 *  - everything else (401 bad credentials, 409 duplicate email, network) has no
 *    single field and belongs in a toast.
 *
 * Returns true when the error was placed on a field, so callers know whether a
 * toast is still needed.
 *
 * `prefix` bridges a shape difference: the backend reports nested lawyer fields
 * flat (`barCouncilNumber`) while the form path is nested
 * (`lawyerProfile.barCouncilNumber`).
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  options?: { prefix?: string; knownFields?: readonly string[] },
): boolean {
  if (!isApiError(error) || !error.fieldErrors) return false;

  const entries = Object.entries(error.fieldErrors);
  if (entries.length === 0) return false;

  let applied = false;

  for (const [field, message] of entries) {
    const prefixed =
      options?.prefix && options.knownFields?.includes(field)
        ? `${options.prefix}.${field}`
        : field;

    setError(prefixed as Path<T>, { type: "server", message });
    applied = true;
  }

  return applied;
}
