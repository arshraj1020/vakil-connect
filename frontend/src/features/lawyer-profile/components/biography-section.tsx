"use client";

import type { UseFormRegister } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  MAX_BIO_LENGTH,
  type LawyerProfileFormValues,
} from "../schemas/lawyer-profile-schema";

/**
 * The bio, with a live character budget.
 *
 * `@Size(max = 2000)` is a hard server rule, so the count is shown as the
 * lawyer types rather than only on submit - discovering a 2100-character bio is
 * over the limit after writing it is the worst possible moment to be told.
 *
 * The counter is `aria-live="polite"`: it should be announced when the limit is
 * approached, but not interrupt every keystroke.
 */
export function BiographySection({
  register,
  error,
  length,
}: {
  register: UseFormRegister<LawyerProfileFormValues>;
  error?: string;
  /** Current length, watched by the parent so the count updates as you type. */
  length: number;
}) {
  const remaining = MAX_BIO_LENGTH - length;
  const isOver = remaining < 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle>About your practice</CardTitle>
        <CardDescription>
          The first thing a client reads. Describe the work you take on and how
          you approach it.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <FormField label="Biography" error={error} required>
          {(field) => (
            <Textarea
              {...field}
              {...register("bio")}
              rows={8}
              placeholder="I practise primarily in family and civil matters, with fifteen years before the district courts..."
            />
          )}
        </FormField>

        <p
          aria-live="polite"
          className={
            isOver
              ? "mt-2 text-xs font-medium text-destructive"
              : "mt-2 text-xs text-muted-foreground"
          }
        >
          {isOver
            ? `${Math.abs(remaining)} characters over the limit`
            : `${remaining} characters remaining`}
        </p>
      </CardContent>
    </Card>
  );
}
