import { z } from "zod";

import { optionalPhoneNumberSchema } from "@/lib/validation";

/**
 * The two editable client fields.
 *
 * Mirrors `UpdateClientProfileRequest`:
 *   fullName     @NotBlank @Size(max = 150)
 *   phoneNumber  @Pattern("^\\+?[0-9]{10,15}$"), optional
 *
 * The phone rule is imported, not restated - registration collects the same
 * field under the same constraint, so both forms share one definition.
 *
 * `email` and `role` are absent because no endpoint can change them.
 */
export const clientProfileSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, "Full name is required")
    .max(150, "Full name cannot exceed 150 characters"),
  phoneNumber: optionalPhoneNumberSchema,
});

export type ClientProfileFormValues = z.infer<typeof clientProfileSchema>;

/**
 * Field names as the BACKEND reports them in `fieldErrors`.
 *
 * Identical to the form's own names - this form is flat - so
 * `applyServerFieldErrors` needs no prefix, unlike registration where the same
 * two fields sit alongside a nested `lawyerProfile.`.
 */
export const CLIENT_PROFILE_FIELD_NAMES = ["fullName", "phoneNumber"] as const;
