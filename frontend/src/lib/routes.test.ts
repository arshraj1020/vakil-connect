import { describe, expect, it } from "vitest";

import {
  ROUTES,
  dashboardFor,
  isProtectedPath,
  roleForPath,
  safeRedirect,
} from "@/lib/routes";

/**
 * Routing and redirect logic.
 *
 * THIS IS THE MODULE THAT SHIPPED A SECURITY BUG. During integration testing
 * `next` was taken from the query string and used unvalidated, which was both an
 * open redirect and the cause of the "Access Denied" report - a stale `next`
 * from a previous session sent a CLIENT to /lawyer/dashboard. `safeRedirect`
 * was written to fix it and had no tests until now.
 *
 * Pure functions, no DOM, no network. If any test here needs a mock, something
 * has been coupled that should not be.
 */

describe("dashboardFor", () => {
  it("sends each role to its own dashboard", () => {
    expect(dashboardFor("CLIENT")).toBe(ROUTES.CLIENT_DASHBOARD);
    expect(dashboardFor("LAWYER")).toBe(ROUTES.LAWYER_DASHBOARD);
    expect(dashboardFor("ADMIN")).toBe(ROUTES.ADMIN_DASHBOARD);
  });

  it("returns a path inside that role's own prefix", () => {
    // Guards against a copy-paste that points two roles at one dashboard.
    expect(roleForPath(dashboardFor("CLIENT"))).toBe("CLIENT");
    expect(roleForPath(dashboardFor("LAWYER"))).toBe("LAWYER");
    expect(roleForPath(dashboardFor("ADMIN"))).toBe("ADMIN");
  });
});

describe("roleForPath", () => {
  it("identifies the owning role from the prefix", () => {
    expect(roleForPath("/client/appointments")).toBe("CLIENT");
    expect(roleForPath("/lawyer/availability")).toBe("LAWYER");
    expect(roleForPath("/admin/users")).toBe("ADMIN");
  });

  it("matches the bare prefix as well as its children", () => {
    expect(roleForPath("/client")).toBe("CLIENT");
    expect(roleForPath("/client/")).toBe("CLIENT");
  });

  it("returns null for public paths", () => {
    expect(roleForPath("/")).toBeNull();
    expect(roleForPath("/lawyers")).toBeNull();
    expect(roleForPath("/about")).toBeNull();
    expect(roleForPath("/login")).toBeNull();
  });

  /**
   * `/lawyers` (public search) and `/lawyer` (the lawyer portal) differ by one
   * character. A prefix check written as `startsWith("/lawyer")` would classify
   * the public search page as lawyer-owned and hide it behind a guard.
   */
  it("does not confuse /lawyers with the /lawyer portal", () => {
    expect(roleForPath("/lawyers")).toBeNull();
    expect(roleForPath("/lawyers/abc-123")).toBeNull();
    expect(roleForPath("/lawyer")).toBe("LAWYER");
  });
});

describe("isProtectedPath", () => {
  it("is true only for role-owned paths", () => {
    expect(isProtectedPath("/client/dashboard")).toBe(true);
    expect(isProtectedPath("/lawyer/profile")).toBe(true);
    expect(isProtectedPath("/admin/users")).toBe(true);

    expect(isProtectedPath("/")).toBe(false);
    expect(isProtectedPath("/lawyers")).toBe(false);
    expect(isProtectedPath("/pricing")).toBe(false);
  });
});

describe("safeRedirect", () => {
  describe("rejects anything that is not a same-site path", () => {
    /*
     * The open-redirect cases. Each of these, returned unchanged, would send a
     * freshly-authenticated user to an attacker-controlled origin - with the
     * credibility of having just logged in to the real site.
     */
    it.each([
      ["absolute http URL", "http://evil.example/steal"],
      ["absolute https URL", "https://evil.example/steal"],
      ["protocol-relative URL", "//evil.example/steal"],
      ["backslash-escaped authority", "/\\evil.example"],
      ["javascript scheme", "javascript:alert(1)"],
      ["relative path without a leading slash", "client/dashboard"],
    ])("falls back for %s", (_label, candidate) => {
      expect(safeRedirect(candidate, "CLIENT")).toBe(ROUTES.CLIENT_DASHBOARD);
    });

    it.each([
      ["null", null],
      ["undefined", undefined],
      ["empty string", ""],
    ])("falls back for %s", (_label, candidate) => {
      expect(safeRedirect(candidate, "LAWYER")).toBe(ROUTES.LAWYER_DASHBOARD);
    });

    it("falls back for a non-string, which a query parser can produce", () => {
      // `?next=a&next=b` yields an array in some parsers.
      expect(safeRedirect(["/client/x"] as unknown as string, "CLIENT")).toBe(
        ROUTES.CLIENT_DASHBOARD,
      );
    });
  });

  describe("rejects another role's territory", () => {
    /**
     * The "Access Denied" bug exactly: a CLIENT signs in carrying a stale
     * `next=/lawyer/dashboard`, is redirected there, and is bounced by the role
     * guard into an error they cannot explain.
     */
    it("does not send a client into the lawyer portal", () => {
      expect(safeRedirect("/lawyer/dashboard", "CLIENT")).toBe(
        ROUTES.CLIENT_DASHBOARD,
      );
    });

    it("does not send a lawyer into the admin portal", () => {
      expect(safeRedirect("/admin/users", "LAWYER")).toBe(
        ROUTES.LAWYER_DASHBOARD,
      );
    });

    it("does not send an admin into the client portal", () => {
      expect(safeRedirect("/client/appointments", "ADMIN")).toBe(
        ROUTES.ADMIN_DASHBOARD,
      );
    });

    it("checks the path only, ignoring query and fragment", () => {
      // A role prefix hidden behind a query string must still be caught.
      expect(safeRedirect("/lawyer/dashboard?from=x", "CLIENT")).toBe(
        ROUTES.CLIENT_DASHBOARD,
      );
      expect(safeRedirect("/admin/users#top", "CLIENT")).toBe(
        ROUTES.CLIENT_DASHBOARD,
      );
    });
  });

  describe("preserves legitimate destinations", () => {
    it("returns a path the role owns", () => {
      expect(safeRedirect("/client/appointments", "CLIENT")).toBe(
        "/client/appointments",
      );
      expect(safeRedirect("/lawyer/availability", "LAWYER")).toBe(
        "/lawyer/availability",
      );
    });

    it("keeps the query string and fragment intact", () => {
      expect(safeRedirect("/client/appointments?status=PENDING", "CLIENT")).toBe(
        "/client/appointments?status=PENDING",
      );
      expect(safeRedirect("/lawyer/reviews#latest", "LAWYER")).toBe(
        "/lawyer/reviews#latest",
      );
    });

    /**
     * A public path is owned by nobody, so any role may be sent there. This is
     * what lets "sign in to book" return the client to the lawyer they were
     * looking at.
     */
    it("allows any role to land on a public path", () => {
      expect(safeRedirect("/lawyers/abc-123", "CLIENT")).toBe("/lawyers/abc-123");
      expect(safeRedirect("/lawyers", "ADMIN")).toBe("/lawyers");
    });
  });
});
