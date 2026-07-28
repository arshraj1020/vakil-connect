"use client";

import { Controller } from "react-hook-form";
import type { Control, FieldErrors, UseFormRegister } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { FormRow } from "@/components/forms/form-section";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { SpecializationPicker } from "@/features/auth/components/specialization-picker";
import type { LawyerProfileFormValues } from "../schemas/lawyer-profile-schema";

/**
 * The four scalar professional fields plus practice areas.
 *
 * `SpecializationPicker` is reused from registration rather than reimplemented:
 * it already solves the accessible multi-select correctly (native checkboxes in
 * a fieldset with a legend), and a second implementation would be the exact
 * duplication the brief forbids. It owns its own label and error markup, which
 * is why it sits outside FormField - a group needs <legend>, not <label>.
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
            <Input
              {...field}
              {...register("city")}
              autoComplete="address-level2"
              placeholder="Mumbai"
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

        <Controller
          control={control}
          name="specializations"
          render={({ field, fieldState }) => (
            <SpecializationPicker
              value={field.value}
              onChange={field.onChange}
              error={fieldState.error?.message}
              hint="Choose every area you practise in. Clients filter by these."
              required
              legend="Practice areas"
            />
          )}
        />
      </CardContent>
    </Card>
  );
}
