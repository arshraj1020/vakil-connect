import api from "@/lib/axios";

/**
 * Email verification and password reset (backend Phases 4 and 6).
 *
 * Pure HTTP, like every other service here: issue a request, return the typed
 * DTO, let errors propagate as the `ApiError` the Axios interceptor builds.
 *
 * TOKENS TRAVEL IN THE BODY, NEVER THE QUERY STRING. The emailed link points at
 * a frontend page which reads `?token=` and POSTs it here. A GET that consumed
 * the token would be fired by mail scanners and link prefetchers before the
 * user ever clicked, burning a single-use token and showing them "invalid link".
 */

const ENDPOINTS = {
  verifyEmail: "/api/auth/verify-email",
  resendVerification: "/api/auth/resend-verification",
  forgotPassword: "/api/auth/forgot-password",
  resetPassword: "/api/auth/reset-password",
} as const;

export interface VerificationResponse {
  verified: boolean;
  message: string;
}

/** Deliberately uninformative - identical for every input. */
export interface AcknowledgementResponse {
  message: string;
}

export interface PasswordResetResponse {
  reset: boolean;
  message: string;
}

/**
 * Consumes a verification token.
 *
 * Throws `ApiError` with `code`:
 *   TOKEN_INVALID (400) · TOKEN_EXPIRED (410) · TOKEN_ALREADY_USED (409)
 */
export async function verifyEmail(token: string): Promise<VerificationResponse> {
  const { data } = await api.post<VerificationResponse>(ENDPOINTS.verifyEmail, {
    token,
  });
  return data;
}

/**
 * Requests another verification email.
 *
 * ALWAYS 202 with an identical body, whether the address is unknown, already
 * verified or inactive - the endpoint must not reveal which. The only other
 * outcome is 429 COOLDOWN_ACTIVE with a `Retry-After` header.
 */
export async function resendVerification(
  email: string,
): Promise<AcknowledgementResponse> {
  const { data } = await api.post<AcknowledgementResponse>(
    ENDPOINTS.resendVerification,
    { email },
  );
  return data;
}

/** Requests a password-reset link. Same constant-response rule as above. */
export async function forgotPassword(
  email: string,
): Promise<AcknowledgementResponse> {
  const { data } = await api.post<AcknowledgementResponse>(
    ENDPOINTS.forgotPassword,
    { email },
  );
  return data;
}

/**
 * Applies a new password and ends every existing session.
 *
 * Returns no token: the user signs in afterwards with the password they chose.
 */
export async function resetPassword(
  token: string,
  newPassword: string,
): Promise<PasswordResetResponse> {
  const { data } = await api.post<PasswordResetResponse>(
    ENDPOINTS.resetPassword,
    { token, newPassword },
  );
  return data;
}

export const identityService = {
  verifyEmail,
  resendVerification,
  forgotPassword,
  resetPassword,
} as const;
