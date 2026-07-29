import { z } from "zod";

/**
 * Validation primitives shared across forms.
 *
 * Rules that more than one form needs live here so they are stated once.
 * Form-specific composition stays in each feature's schema module.
 */

/**
 * Phone number, mirroring `@Pattern("^\\+?[0-9]{10,15}$")` on the backend.
 *
 * Used by `UpdateClientProfileRequest.phoneNumber` and by `RegisterRequest`.
 * Both DTOs declare the identical constraint, so the pattern is defined once
 * rather than restated per form.
 */
export const PHONE_PATTERN = /^\+?[0-9]{10,15}$/;

/**
 * An optional phone number field.
 *
 * Empty is accepted here because the field is optional on both endpoints. The
 * CALLER is responsible for omitting the key entirely when the value is blank:
 * Bean Validation's `@Pattern` passes `null` but rejects `""`, so sending an
 * empty string would fail server-side validation where omitting it succeeds.
 */
export const optionalPhoneNumberSchema = z
  .string()
  .trim()
  .refine((value) => value === "" || PHONE_PATTERN.test(value), {
    message: "Enter a valid phone number",
  });
