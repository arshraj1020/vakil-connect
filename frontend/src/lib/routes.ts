import type { Role } from "@/types";

/**
 * Every application route in one place.
 *
 * Consumed by the Axios 401 redirect, edge middleware, route guards and every
 * <Link>. Centralising them means a path rename is a single edit and a typo is
 * a compile error rather than a silent 404.
 */
export const ROUTES = {
  /* Public */
  HOME: "/",
  ABOUT: "/about",
  PRICING: "/pricing",
  LOGIN: "/login",
  REGISTER: "/register",
  /* Identity (Phase 7). Public: a user who cannot sign in must still reach
     these, and the emailed links point straight at them. */
  VERIFY_EMAIL: "/verify-email",
  FORGOT_PASSWORD: "/forgot-password",
  RESET_PASSWORD: "/reset-password",
  LAWYERS: "/lawyers",
  lawyerDetail: (id: string) => `/lawyers/${id}` as const,

  /* Client */
  CLIENT_DASHBOARD: "/client/dashboard",
  CLIENT_PROFILE: "/client/profile",
  CLIENT_APPOINTMENTS: "/client/appointments",
  /** Booking flow. Not built yet; the lawyer profile CTA already points here. */
  bookAppointment: (lawyerId: string) =>
    `/client/appointments/book?lawyerId=${lawyerId}` as const,

  /* Lawyer */
  LAWYER_DASHBOARD: "/lawyer/dashboard",
  LAWYER_PROFILE: "/lawyer/profile",
  LAWYER_APPOINTMENTS: "/lawyer/appointments",
  LAWYER_AVAILABILITY: "/lawyer/availability",
  LAWYER_REVIEWS: "/lawyer/reviews",

  /* Admin */
  ADMIN_DASHBOARD: "/admin/dashboard",
  ADMIN_LAWYERS: "/admin/lawyers",
  ADMIN_REVIEWS: "/admin/reviews",
  ADMIN_USERS: "/admin/users",
} as const;

/** Route prefixes owned by each role, used by middleware and guards. */
export const ROLE_ROUTE_PREFIX: Record<Role, string> = {
  CLIENT: "/client",
  LAWYER: "/lawyer",
  ADMIN: "/admin",
};

/**
 * Where a user lands after logging in, and where a guard sends someone who
 * wandered into another role's section.
 */
export function dashboardFor(role: Role): string {
  switch (role) {
    case "CLIENT":
      return ROUTES.CLIENT_DASHBOARD;
    case "LAWYER":
      return ROUTES.LAWYER_DASHBOARD;
    case "ADMIN":
      return ROUTES.ADMIN_DASHBOARD;
  }
}

/** True when the path belongs to a role-protected section. */
export function isProtectedPath(pathname: string): boolean {
  return Object.values(ROLE_ROUTE_PREFIX).some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}

/** The role that owns a protected path, or null for public paths. */
export function roleForPath(pathname: string): Role | null {
  const entry = (Object.entries(ROLE_ROUTE_PREFIX) as Array<[Role, string]>).find(
    ([, prefix]) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
  return entry ? entry[0] : null;
}

/**
 * Resolves where to send a user after they sign in.
 *
 * The `next` parameter is attacker-controllable - it comes straight from the
 * query string - and it may also be legitimately STALE, captured before a
 * different account signed in. Both cases are handled here so that every
 * redirect site shares one rule.
 *
 * Rejects, falling back to the user's own dashboard:
 *
 *  - anything that is not a string (absent parameter)
 *  - anything not beginning with "/" - an absolute URL such as
 *    "https://evil.com" would otherwise navigate off-site immediately after a
 *    successful sign-in, which is a credible phishing vector
 *  - "//host" and "/\host", which browsers resolve as protocol-relative URLs
 *    and are therefore external despite the leading slash
 *  - a path owned by a DIFFERENT role, which is what previously stranded a
 *    CLIENT on /lawyer/dashboard looking at an "Access denied" page after
 *    signing out of a lawyer account and back in as a client
 *
 * Accepts a path owned by no role (e.g. "/lawyers"), because public
 * destinations are valid for everyone.
 *
 * Note the deliberate consequence: signing out and back in as the SAME role
 * still returns the user to where they were, which is the behaviour the `next`
 * parameter exists to provide.
 */
export function safeRedirect(next: string | null | undefined, role: Role): string {
  const fallback = dashboardFor(role);

  if (typeof next !== "string" || next.length === 0) return fallback;

  // Must be site-relative, and must not be protocol-relative.
  if (!next.startsWith("/")) return fallback;
  if (next.startsWith("//") || next.startsWith("/\\")) return fallback;

  // Compare on the path alone; `next` may carry a query string or fragment.
  const pathname = next.split(/[?#]/)[0] ?? "";

  const owner = roleForPath(pathname);
  if (owner !== null && owner !== role) return fallback;

  return next;
}
