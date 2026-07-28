import Cookies from "js-cookie";

import { AUTH_COOKIE } from "./constants";

/**
 * Browser-side JWT persistence.
 *
 * This module is deliberately dependency-free apart from the cookie library and
 * the shared constant. Both the Axios instance and the Zustand auth store need
 * the token; if either imported the other the graph would be circular (Axios
 * needs the token, the store needs Axios to fetch the session), which in ESM
 * surfaces as an intermittently `undefined` import. Keeping the primitive
 * separate breaks that cycle structurally rather than by convention.
 *
 * Client-only: `js-cookie` touches `document`. Edge middleware reads the same
 * cookie through `NextRequest.cookies` using {@link AUTH_COOKIE}.
 */

/** Reads the stored JWT, or null when absent or running on the server. */
export function getStoredToken(): string | null {
  if (typeof document === "undefined") return null;
  return Cookies.get(AUTH_COOKIE.name) ?? null;
}

/**
 * Persists the JWT.
 *
 * `secure` is enabled outside development so the cookie is never sent over
 * plain HTTP in production. `sameSite: lax` allows normal top-level navigation
 * while blocking cross-site form posts.
 */
export function setStoredToken(token: string): void {
  if (typeof document === "undefined") return;

  Cookies.set(AUTH_COOKIE.name, token, {
    expires: AUTH_COOKIE.maxAgeDays,
    path: AUTH_COOKIE.path,
    sameSite: AUTH_COOKIE.sameSite,
    secure: process.env.NODE_ENV === "production",
  });
}

/** Removes the JWT. Must use the same path the cookie was written with. */
export function clearStoredToken(): void {
  if (typeof document === "undefined") return;
  Cookies.remove(AUTH_COOKIE.name, { path: AUTH_COOKIE.path });
}

/** Convenience check used by guards before attempting a session bootstrap. */
export function hasStoredToken(): boolean {
  return getStoredToken() !== null;
}
