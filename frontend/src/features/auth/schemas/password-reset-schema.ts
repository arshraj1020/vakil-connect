import { z } from "zod";

/**
 * Password rules, mirrored from the backend.
 *
 * THE BACKEND REMAINS AUTHORITATIVE. `PasswordRules` in
 * `auth/dto/PasswordRules.java` is the single source of truth and validates
 * every request regardless of what this file says; there is no machine-readable
 * policy endpoint to fetch it from, so the value is duplicated here purely to
 * give immediate feedback before a round trip.
 *
 * Keep MIN_PASSWORD_LENGTH equal to PasswordRules.MIN_LENGTH. If they drift, the
 * failure is benign in one direction (the server rejects what the client
 * allowed) and merely annoying in the other (the client blocks something the
 * server would accept) - never a security hole, because the client cannot
 * loosen the server.
 */
export const MIN_PASSWORD_LENGTH = 8;

export const PASSWORD_LENGTH_MESSAGE =
  "Password must be at least 8 characters";

export const forgotPasswordSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Enter a valid email address"),
});

export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    newPassword: z.string().min(MIN_PASSWORD_LENGTH, PASSWORD_LENGTH_MESSAGE),
    confirmPassword: z.string().min(1, "Please confirm your password"),
  })
  /*
   * Confirmation is a CLIENT-ONLY concern and is deliberately absent from the
   * API payload: the backend has no business knowing the user typed it twice.
   * The error is attached to the confirm field so it renders beside the input
   * the user must fix, not at the top of the form.
   */
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;
