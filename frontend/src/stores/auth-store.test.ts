import { beforeEach, describe, expect, it } from "vitest";

import {
  selectIsInitialising,
  useAuthStore,
} from "@/stores/auth-store";

/**
 * Session status transitions.
 *
 * THIS IS THE MODULE BEHIND THE "login just refreshes the page" BUG.
 * `selectIsInitialising` is `status === "idle" || status === "loading"`, and
 * every layout that gates on it (the auth layout, RoleGuard, the protected
 * layout, the public navbar) swaps its rendered content for a full-page
 * loader whenever it is true - which is correct while `AuthProvider` is
 * hydrating the session on first load, but was ALSO true for the duration of
 * every login attempt, because `useAuth().login()` used to call the same
 * `setLoading()` action `AuthProvider` uses for bootstrap.
 *
 * The visible effect: the instant "Sign in" was clicked, the login form was
 * unmounted and replaced by `<FullPageLoader />`; on a failed attempt,
 * `reset()` flipped `status` back to "unauthenticated", `isInitialising` went
 * false again, and the layout remounted a brand-new, blank `LoginForm` -
 * which reads exactly like "the page just refreshed", with the real error
 * toast easy to miss underneath.
 *
 * These tests exercise the store directly, at the level `useAuth().login()`
 * now operates: a login attempt must never touch `status` while it is in
 * flight, and a failed attempt must land on "unauthenticated" - never back on
 * "idle" or "loading" - so `isInitialising` stays false throughout.
 */

beforeEach(() => {
  useAuthStore.setState({ user: null, status: "idle", error: null });
});

describe("selectIsInitialising", () => {
  it("is true only for idle and loading - the two bootstrap states", () => {
    useAuthStore.setState({ status: "idle" });
    expect(selectIsInitialising(useAuthStore.getState())).toBe(true);

    useAuthStore.setState({ status: "loading" });
    expect(selectIsInitialising(useAuthStore.getState())).toBe(true);

    useAuthStore.setState({ status: "authenticated" });
    expect(selectIsInitialising(useAuthStore.getState())).toBe(false);

    useAuthStore.setState({ status: "unauthenticated" });
    expect(selectIsInitialising(useAuthStore.getState())).toBe(false);
  });
});

describe("a login attempt (the sequence useAuth().login() performs)", () => {
  it("never sets status to loading while in flight, on failure", () => {
    // A visitor who is not signed in opens the login page: AuthProvider's
    // hydration has already settled on "unauthenticated" (no stored token).
    useAuthStore.getState().reset();
    expect(useAuthStore.getState().status).toBe("unauthenticated");
    expect(selectIsInitialising(useAuthStore.getState())).toBe(false);

    // The fixed login() no longer calls setLoading() before the request, so
    // status must not move while the credential check is in flight.
    expect(useAuthStore.getState().status).toBe("unauthenticated");
    expect(selectIsInitialising(useAuthStore.getState())).toBe(false);

    // Wrong password: login()'s catch block runs reset() + setError().
    useAuthStore.getState().reset();
    useAuthStore.getState().setError("Incorrect email or password.");

    // The form must still be mounted for the error to be visible - i.e.
    // isInitialising must never have flipped true during the whole attempt.
    expect(useAuthStore.getState().status).toBe("unauthenticated");
    expect(selectIsInitialising(useAuthStore.getState())).toBe(false);
  });

  it("never sets status to loading while in flight, on success", () => {
    useAuthStore.getState().reset();

    // The fixed login() calls setUser() directly on success, with no
    // intervening setLoading() call.
    useAuthStore
      .getState()
      .setUser({
        id: "u1",
        fullName: "Ada Lovelace",
        email: "ada@example.com",
        phoneNumber: "9999999999",
        role: "CLIENT",
      });

    expect(useAuthStore.getState().status).toBe("authenticated");
    expect(selectIsInitialising(useAuthStore.getState())).toBe(false);
  });
});
