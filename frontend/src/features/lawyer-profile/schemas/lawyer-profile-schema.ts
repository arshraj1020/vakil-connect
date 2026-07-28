import { z } from "zod";

/**
 * The editable lawyer profile fields, and the single definition of their rules.
 *
 * These same six fields are collected twice in the product - once nested inside
 * registration, once flat on the profile screen - so the rules live here and
 * both consumers import them. `register-schema.ts` delegates to this schema
 * rather than restating it, which is why a change to the bio limit or the fee
 * rule cannot drift between the two forms.
 *
 * Mirrors `UpdateLawyerProfileRequest` exactly:
 *   experienceYears  @NotNull @Min(0)
 *   bio              @NotBlank @Size(max = 2000)
 *   consultationFee  @NotNull @DecimalMin(value = "0.0", inclusive = false)
 *   city             @NotBlank
 *   officeAddress    @NotBlank
 *   specializations  @NotNull @Size(min = 1)
 *
 * `barCouncilNumber` is deliberately absent: the update DTO does not carry it,
 * so it is set once at registration and never edited.
 *
 * Numbers are held as STRINGS, matching the convention established in
 * registration: an <input> always yields a string, and `z.coerce.number()`
 * would make the schema's input and output types diverge, which React Hook
 * Form then needs extra generics to reconcile. Parsing happens once, at submit.
 */

export const MAX_BIO_LENGTH = 2000;

/**
 * Not a backend rule - the server accepts any non-negative integer. It catches
 * a mistyped "202" where "20" was meant, which the server would happily store.
 */
export const MAX_EXPERIENCE_YEARS = 80;

export const lawyerProfileSchema = z
  .object({
    experienceYears: z.string().trim(),
    consultationFee: z.string().trim(),
    city: z.string().trim(),
    officeAddress: z.string().trim(),
    bio: z.string().trim(),
    specializations: z.array(z.string().min(1)),
  })
  .superRefine((profile, ctx) => {
    const addIssue = (field: keyof typeof profile, message: string) => {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [field],
        message,
      });
    };

    if (!profile.city) addIssue("city", "City is required");
    if (!profile.officeAddress) {
      addIssue("officeAddress", "Office address is required");
    }

    if (!profile.bio) {
      addIssue("bio", "A short bio is required");
    } else if (profile.bio.length > MAX_BIO_LENGTH) {
      addIssue("bio", `Bio cannot exceed ${MAX_BIO_LENGTH} characters`);
    }

    if (!profile.experienceYears) {
      addIssue("experienceYears", "Years of experience is required");
    } else {
      const years = Number(profile.experienceYears);
      // Number("abc") is NaN, which is not an integer - so this also rejects
      // non-numeric input without a separate check.
      if (!Number.isInteger(years)) addIssue("experienceYears", "Enter a whole number");
      else if (years < 0) addIssue("experienceYears", "Experience cannot be negative");
      else if (years > MAX_EXPERIENCE_YEARS) {
        addIssue("experienceYears", "Enter a realistic number of years");
      }
    }

    if (!profile.consultationFee) {
      addIssue("consultationFee", "Consultation fee is required");
    } else {
      const fee = Number(profile.consultationFee);
      if (Number.isNaN(fee)) addIssue("consultationFee", "Enter an amount");
      // inclusive = false on the backend, so exactly 0 is rejected too.
      else if (fee <= 0) addIssue("consultationFee", "Fee must be greater than 0");
    }

    if (profile.specializations.length === 0) {
      addIssue("specializations", "Select at least one specialization");
    }
  });

export type LawyerProfileFormValues = z.infer<typeof lawyerProfileSchema>;

/**
 * The field names the BACKEND reports in `fieldErrors`.
 *
 * Identical to the form's own field names, because this form is flat - so
 * `applyServerFieldErrors` needs no prefix here, unlike registration where the
 * same fields sit under `lawyerProfile.`.
 */
export const LAWYER_PROFILE_FIELD_NAMES = [
  "experienceYears",
  "consultationFee",
  "city",
  "officeAddress",
  "bio",
  "specializations",
] as const;
