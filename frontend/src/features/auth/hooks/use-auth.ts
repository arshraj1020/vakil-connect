"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

import { clearStoredToken, setStoredToken } from "@/lib/auth-storage";
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
import { isApiError, type LoginRequest, type RegisterRequest, type RegisterResponse, type Role } from "@/types";

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
   */
  const logout = useCallback(() => {
    clearStoredToken();
    reset();
    queryClient.clear();
  }, [queryClient, reset]);

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
    hasRole,
  };
}
