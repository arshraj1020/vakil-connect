import { create } from "zustand";

import type { CurrentUserResponse, Role } from "@/types";

/**
 * Session state.
 *
 * This store is a PURE state container: it imports types only - no Axios, no
 * services, no React. Async work happens in `AuthProvider` and `useAuth`, which
 * call a service and then write the result here.
 *
 * That constraint is what keeps the dependency graph acyclic (the HTTP layer
 * needs the token, and anything fetching the session needs the HTTP layer), and
 * it makes the store trivially testable without mocking a network.
 *
 * Why the session lives here rather than in TanStack Query: it is read
 * synchronously by almost every render (guards, navigation, conditional UI) yet
 * changes at most twice per visit. Modelling it as a query would force every
 * consumer to handle loading/error states for something already resolved, and
 * would create a second source of truth alongside this store.
 *
 * The store is intentionally NOT persisted to localStorage. The cookie is the
 * persistence layer; a mirrored copy could disagree with `getCurrentUser()`.
 */

/**
 * Explicit lifecycle rather than a pair of booleans, so impossible combinations
 * (e.g. "loading AND authenticated") cannot be represented.
 *
 *   idle            - provider has not run yet
 *   loading         - hydrating, or a login is in flight
 *   authenticated   - `user` is non-null and confirmed by the backend
 *   unauthenticated - no valid session
 */
export type AuthStatus =
  | "idle"
  | "loading"
  | "authenticated"
  | "unauthenticated";

interface AuthState {
  user: CurrentUserResponse | null;
  status: AuthStatus;
  /** Message from the last failed auth operation, for display by forms. */
  error: string | null;

  /** Marks an auth operation as in flight and clears any previous error. */
  setLoading: () => void;
  /** Records a confirmed session. */
  setUser: (user: CurrentUserResponse) => void;
  /** Records a failure without clearing an existing session. */
  setError: (message: string) => void;
  /** Clears the session; used on logout and on failed hydration. */
  reset: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  user: null,
  status: "idle",
  error: null,

  setLoading: () => set({ status: "loading", error: null }),

  setUser: (user) => set({ user, status: "authenticated", error: null }),

  setError: (message) => set({ error: message }),

  reset: () => set({ user: null, status: "unauthenticated", error: null }),
}));

/* ------------------------------------------------------------- selectors --
 * Exported as standalone selectors so components subscribe to one slice and
 * re-render only when that slice changes:
 *
 *     const user = useAuthStore(selectUser);
 */

export const selectUser = (state: AuthState): CurrentUserResponse | null =>
  state.user;

export const selectStatus = (state: AuthState): AuthStatus => state.status;

export const selectRole = (state: AuthState): Role | null =>
  state.user?.role ?? null;

export const selectIsAuthenticated = (state: AuthState): boolean =>
  state.status === "authenticated" && state.user !== null;

/** True while the initial hydration has not settled - used to gate guards. */
export const selectIsInitialising = (state: AuthState): boolean =>
  state.status === "idle" || state.status === "loading";
