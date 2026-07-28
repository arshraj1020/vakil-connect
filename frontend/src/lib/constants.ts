/**
 * Application-wide constants.
 *
 * Deliberately free of imports so that any layer - including edge middleware,
 * which cannot use browser APIs - can consume these values.
 */

/**
 * The cookie holding the JWT.
 *
 * A cookie rather than localStorage so that edge middleware can read it and
 * protect routes before a protected page renders. It is NOT httpOnly, because
 * the backend returns the token in the response body and cannot set a cookie
 * itself; the XSS exposure is therefore equivalent to localStorage, with the
 * added benefit of server-side route protection. Moving to an httpOnly cookie
 * would require a BFF proxy and is tracked as a v2 hardening item.
 */
export const AUTH_COOKIE = {
  name: "vc_token",
  /** 24h, matching the backend's `jwt.expiration` of 86400000ms. */
  maxAgeDays: 1,
  path: "/",
  sameSite: "lax",
} as const;

/** Default page size; mirrors the backend's own default. */
export const DEFAULT_PAGE_SIZE = 10;

/**
 * Upper bound applied client-side when building paged requests.
 *
 * The backend does not cap `size`, so this prevents the UI from ever asking
 * for an unbounded page.
 */
export const MAX_PAGE_SIZE = 100;

/** Query string flag appended when a session expires, so /login can explain why. */
export const SESSION_EXPIRED_PARAM = "session";
export const SESSION_EXPIRED_VALUE = "expired";

/**
 * Query string carrying the path a user was heading to before being redirected
 * to sign in, so login can return them there.
 */
export const REDIRECT_PARAM = "next";
