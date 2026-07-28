import { NextResponse, type NextRequest } from "next/server";

import { AUTH_COOKIE, REDIRECT_PARAM } from "@/lib/constants";
import { ROUTES, isProtectedPath } from "@/lib/routes";

/**
 * Edge route protection.
 *
 * Scope is deliberately narrow: this middleware answers exactly one question -
 * "does a token cookie exist?" - and redirects to the login screen when a
 * protected path is requested without one.
 *
 * It does NOT check roles. The backend's JWT carries only `sub`, `iat` and
 * `exp` (see JwtService.generateToken) - there is no role claim - so the edge
 * physically cannot determine whether a CLIENT is trying to open an ADMIN page.
 * Role authorization therefore lives in RoleGuard, which has the user record
 * fetched from `/api/users/me`. The two layers answer different questions
 * against different data, so no authorization logic is duplicated.
 *
 * It also does NOT decode the token to check expiry, even though that is
 * possible at the edge. Expiry is already enforced authoritatively by the
 * backend (401) and handled by the Axios interceptor; a third implementation
 * would be the duplication this design avoids. The cost is that a stale cookie
 * passes the edge and is rejected a moment later during hydration.
 *
 * Nor does it redirect signed-in users away from /login: without the role it
 * cannot know which dashboard to send them to. The login page handles that.
 *
 * This is not a security boundary. The backend is - it returns 401/403
 * independently of anything decided here. This exists purely for UX.
 */
export function middleware(request: NextRequest): NextResponse {
  const { pathname, search } = request.nextUrl;

  const hasToken = Boolean(request.cookies.get(AUTH_COOKIE.name)?.value);

  if (isProtectedPath(pathname) && !hasToken) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = ROUTES.LOGIN;
    loginUrl.search = "";
    // Preserve the intended destination so login can return the user to it.
    loginUrl.searchParams.set(REDIRECT_PARAM, `${pathname}${search}`);

    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  /**
   * Skip Next internals and static assets - running middleware on every image
   * request is wasted work.
   */
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)",
  ],
};
