"use client";

import { useEffect, useRef, type ReactNode } from "react";

import { clearStoredToken, hasStoredToken } from "@/lib/auth-storage";
import { userService } from "@/services/user-service";
import { useAuthStore } from "@/stores/auth-store";

/**
 * Bootstraps the session exactly once on application startup.
 *
 * The cookie proves only that a token EXISTS - never that it is valid,
 * unexpired, or that the account is still active. So hydration always asks the
 * backend:
 *
 *   no token          -> unauthenticated
 *   token + 200 /me   -> authenticated, store the returned user
 *   token + failure   -> stale or tampered cookie: clear it, unauthenticated
 *
 * This component renders children immediately rather than blocking on the
 * request. Public pages must not wait for an auth check they do not need;
 * protected routes are gated by middleware and RoleGuard, which read
 * `selectIsInitialising` to hold their own render until this settles.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const setLoading = useAuthStore((s) => s.setLoading);
  const setUser = useAuthStore((s) => s.setUser);
  const reset = useAuthStore((s) => s.reset);

  /**
   * React StrictMode invokes effects twice in development. Without this guard
   * hydration would fire two `/api/users/me` requests on every cold start.
   */
  const hasHydrated = useRef(false);

  useEffect(() => {
    if (hasHydrated.current) return;
    hasHydrated.current = true;

    let cancelled = false;

    async function hydrate(): Promise<void> {
      if (!hasStoredToken()) {
        reset();
        return;
      }

      setLoading();

      try {
        const user = await userService.getCurrentUser();
        if (!cancelled) setUser(user);
      } catch {
        /*
         * Any failure here means the stored token cannot be used. A 401 will
         * already have been cleared by the Axios interceptor; clearing again is
         * harmless and also covers 403 and network failures, so the app never
         * sits in a half-authenticated state.
         *
         * The error is intentionally swallowed: a failed bootstrap is a normal
         * "not signed in" outcome, not something to show the user.
         */
        clearStoredToken();
        if (!cancelled) reset();
      }
    }

    void hydrate();

    return () => {
      cancelled = true;
    };
  }, [reset, setLoading, setUser]);

  return <>{children}</>;
}
