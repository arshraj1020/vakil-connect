"use client";

import { Controller } from "react-hook-form";
import type { Control, FieldErrors, UseFormRegister } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { FormRow } from "@/components/forms/form-section";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CityNameCombobox } from "@/features/reference/components/city-name-combobox";
import { SpecializationMultiSelect } from "@/features/reference/components/specialization-multi-select";
import type { LawyerProfileFormValues } from "../schemas/lawyer-profile-schema";

/**
 * The four scalar professional fields plus practice areas.
 *
 * CITY AND PRACTICE AREAS ARE REFERENCE-BACKED (Frontend Phase A). Both used to
 * be local: city was free text, and practice areas came from a hardcoded
 * constant. Registration had already moved to the reference data in Phase 2D and
 * this form had not, which left two problems:
 *
 *   * a mistyped city is not merely cosmetic. Since the Phase 2G read cut-over
 *     an unresolvable name CLEARS `primary_city_id` and drops the row from
 *     `lawyer_practice_cities` (LawyerServiceImpl#applyCityReference), so one
 *     typo on save removes the lawyer from city search entirely
 *   * the constant and the seeded vocabulary were two sources of truth for one
 *     list, and since Phase 2E the backend REJECTS an unknown practice area with
 *     400 - so the first name that existed in only one of them would start
 *     failing saves
 *
 * The payload is unchanged: `city` is still a name string and `specializations`
 * is still an array of names. Only the controls changed.
 *
 * Both are `Controller`-driven because they are controlled components; the
 * remaining inputs stay uncontrolled via `register`.
 *
 * The numeric inputs use `inputMode` rather than `type="number"`: a number
 * input silently discards non-numeric text, which would hide the very mistake
 * the schema's "Enter a whole number" message exists to report, and its spinner
 * invites nudging a fee by one rupee at a time.
 */
export function ProfessionalInfoSection({
  register,
  control,
  errors,
}: {
  register: UseFormRegister<LawyerProfileFormValues>;
  control: Control<LawyerProfileFormValues>;
  errors: FieldErrors<LawyerProfileFormValues>;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Practice details</CardTitle>
        <CardDescription>
          What clients filter and compare on when searching.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <FormRow>
          <FormField
            label="Years of experience"
            error={errors.experienceYears?.message}
            required
          >
            {(field) => (
              <Input
                {...field}
                {...register("experienceYears")}
                inputMode="numeric"
                autoComplete="off"
                placeholder="12"
              />
            )}
          </FormField>

          <FormField
            label="Consultation fee (₹)"
            error={errors.consultationFee?.message}
            hint="Charged per consultation."
            required
          >
            {(field) => (
              <Input
                {...field}
                {...register("consultationFee")}
                inputMode="decimal"
                autoComplete="off"
                placeholder="1500"
              />
            )}
          </FormField>
        </FormRow>

        <FormField label="City" error={errors.city?.message} required>
          {(field) => (
            <Controller
              control={control}
              name="city"
              render={({ field: controlled }) => (
                <CityNameCombobox
                  id={field.id}
                  aria-invalid={field["aria-invalid"]}
                  aria-describedby={field["aria-describedby"]}
                  value={controlled.value}
                  onChange={controlled.onChange}
                />
              )}
            />
          )}
        </FormField>

        <FormField
          label="Office address"
          error={errors.officeAddress?.message}
          required
        >
          {(field) => (
            <Input
              {...field}
              {...register("officeAddress")}
              autoComplete="street-address"
              placeholder="304, Fort Chambers, MG Road"
            />
          )}
        </FormField>

        <FormField
          label="Practice areas"
          error={errors.specializations?.message}
          hint="Choose every area you practise in. Clients filter by these."
          required
        >
          {(field) => (
            <Controller
              control={control}
              name="specializations"
              render={({ field: controlled }) => (
                <SpecializationMultiSelect
                  id={field.id}
                  aria-invalid={field["aria-invalid"]}
                  aria-describedby={field["aria-describedby"]}
                  value={controlled.value}
                  onChange={controlled.onChange}
                />
              )}
            />
          )}
        </FormField>
      </CardContent>
    </Card>
  );
}
