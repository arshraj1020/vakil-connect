import { z } from "zod";

/**
 * Login form validation.
 *
 * Mirrors the backend's `LoginRequest` constraints (@NotBlank + @Email on
 * email, @NotBlank on password). Deliberately does NOT enforce the 8-character
 * minimum here: on sign-in that would tell an attacker something about stored
 * passwords and would reject legacy accounts. Length is a registration rule.
 */
export const loginSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
  /** UI only - the backend issues a fixed 24h token and has no refresh flow. */
  rememberMe: z.boolean().default(false),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
