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
  LOGIN: "/login",
  REGISTER: "/register",
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
