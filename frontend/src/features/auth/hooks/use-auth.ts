"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useCallback } from "react";

import { clearStoredToken, setStoredToken } from "@/lib/auth-storage";
import { ROUTES } from "@/lib/routes";
import { authService } from "@/services/auth-service";
import { userService } from "@/services/user-service";
import {
  selectIsAuthenticated,
  selectIsInitialising,
  selectRole,
  selectStatus,
  selectUser,
  useAuthStore,
} from "@/stores/auth-store";
import {
  isApiError,
  type CurrentUserResponse,
  type LoginRequest,
  type RegisterRequest,
  type RegisterResponse,
  type Role,
} from "@/types";

/**
 * The single entry point for authentication in the UI.
 *
 * Composes three collaborators that stay unaware of one another:
 *   - `authService` / `userService` - HTTP
 *   - `auth-storage`                - token persistence
 *   - `useAuthStore`                - in-memory session state
 *
 * Components never touch those directly; they call this hook.
 */
export function useAuth() {
  const queryClient = useQueryClient();
  const router = useRouter();

  const user = useAuthStore(selectUser);
  const status = useAuthStore(selectStatus);
  const role = useAuthStore(selectRole);
  const isAuthenticated = useAuthStore(selectIsAuthenticated);
  const isInitialising = useAuthStore(selectIsInitialising);
  const error = useAuthStore((s) => s.error);

  const setLoading = useAuthStore((s) => s.setLoading);
  const setUser = useAuthStore((s) => s.setUser);
  const setError = useAuthStore((s) => s.setError);
  const reset = useAuthStore((s) => s.reset);

  /**
   * Authenticates and establishes a session.
   *
   * Two requests by design: `LoginResponse` omits `id` and `phoneNumber`, so
   * the full account record is read back via `getCurrentUser()`. That keeps a
   * single user shape everywhere and makes the backend - not the login payload
   * - the source of truth.
   *
   * Resolves with the confirmed user so the caller can redirect by role.
   */
  const login = useCallback(
    async (credentials: LoginRequest) => {
      setLoading();

      try {
        const session = await authService.login(credentials);
        setStoredToken(session.token);

        // Must follow token storage: the interceptor reads the cookie.
        const currentUser = await userService.getCurrentUser();
        setUser(currentUser);

        return currentUser;
      } catch (err) {
        // The token may have been written before the second call failed.
        clearStoredToken();
        reset();

        const message = isApiError(err)
          ? err.message
          : "Unable to sign in. Please try again.";
        setError(message);
        throw err;
      }
    },
    [reset, setError, setLoading, setUser],
  );

  /**
   * Creates an account.
   *
   * Does NOT sign the user in - the backend returns no token from registration
   * - so the caller should navigate to the login screen on success.
   */
  const register = useCallback(
    async (payload: RegisterRequest): Promise<RegisterResponse> => {
      try {
        return await authService.register(payload);
      } catch (err) {
        const message = isApiError(err)
          ? err.message
          : "Unable to create your account. Please try again.";
        setError(message);
        throw err;
      }
    },
    [setError],
  );

  /**
   * Ends the session.
   *
   * There is no server-side logout endpoint - JWTs are stateless - so this is
   * purely local: drop the token, reset the store, and clear the query cache.
   *
   * Clearing the cache is not optional: without it the next account to sign in
   * on this browser would briefly render the previous user's cached
   * appointments and dashboard figures.
   *
   * The explicit navigation matters. Without it, signing out left the user on
   * the page they were already on; the protected layout then noticed the lost
   * session and redirected with `?next=<that protected path>`, which is how a
   * stale cross-role destination was manufactured on every sign-out. Navigating
   * here means the common case never captures a `next` at all - and
   * `safeRedirect` independently rejects one if some other path still does.
   *
   * Order is deliberate: clear the token first so no in-flight request can
   * reuse it, then drop the session, then the cache, then navigate.
   */
  const logout = useCallback(() => {
    clearStoredToken();
    reset();
    queryClient.clear();
    router.replace(ROUTES.LOGIN);
  }, [queryClient, reset, router]);

  /**
   * Replaces the session record after the user edits their own account.
   *
   * The app shell reads `fullName` from the store, so a client renaming
   * themselves on the profile screen must be reflected there immediately -
   * otherwise the navbar keeps the old name until a full reload.
   *
   * Exposed here rather than having features write to the store directly, so
   * `useAuth` remains the only way the UI touches session state.
   */
  const updateSessionUser = useCallback(
    (updated: CurrentUserResponse) => setUser(updated),
    [setUser],
  );

  /** Convenience for role-conditional UI; guards use this too. */
  const hasRole = useCallback(
    (expected: Role) => user?.role === expected,
    [user],
  );

  return {
    user,
    role,
    status,
    error,
    isAuthenticated,
    /** True until the initial session check settles - gate guards on this. */
    isInitialising,
    login,
    register,
    logout,
    updateSessionUser,
    hasRole,
  };
}
