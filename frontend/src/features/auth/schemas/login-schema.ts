import { z } from "zod";

/**
 * Login form validation.
 *
 * Mirrors the backend's `LoginRequest` constraints (@NotBlank + @Email on
 * email, @NotBlank on password). Deliberately does NOT enforce the 8-character
 * minimum here: on sign-in that would tell an attacker something about stored
 * passwords and would reject legacy accounts. Length is a registration rule.
 *
 * There is no `rememberMe` field. The backend issues a fixed 24h token
 * (`jwt.expiration: 86400000`) and exposes no refresh endpoint, so no
 * client-side choice can extend or shorten a session's real lifetime - the
 * control existed but changed nothing. See the login form for the full note.
 */
export const loginSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
