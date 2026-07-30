import { beforeEach, describe, expect, it } from "vitest";

import {
  clearStoredToken,
  getStoredToken,
  hasStoredToken,
  setStoredToken,
} from "@/lib/auth-storage";

/**
 * Token persistence.
 *
 * Runs against jsdom's real `document.cookie` rather than a mocked js-cookie:
 * the thing worth testing is that a token written by one call is visible to the
 * next and gone after `clearStoredToken`, and mocking the cookie jar would be
 * testing the mock. The global `afterEach` in test/setup.ts clears cookies so
 * one test cannot satisfy another's assertion.
 *
 * NOT TESTED HERE: the `secure` flag, which js-cookie only sets outside
 * development and which jsdom does not expose on read-back. That is a
 * configuration assertion, not a behavioural one.
 */

beforeEach(() => {
  clearStoredToken();
});

describe("token round trip", () => {
  it("returns null when nothing is stored", () => {
    expect(getStoredToken()).toBeNull();
    expect(hasStoredToken()).toBe(false);
  });

  it("reads back exactly what was written", () => {
    setStoredToken("header.payload.signature");

    expect(getStoredToken()).toBe("header.payload.signature");
    expect(hasStoredToken()).toBe(true);
  });

  it("overwrites rather than accumulating", () => {
    setStoredToken("first");
    setStoredToken("second");

    expect(getStoredToken()).toBe("second");
  });

  /**
   * A JWT is base64url and contains dots; some values also carry `=` padding.
   * If the cookie were written unencoded, everything after a `=` would be lost
   * and the token would come back truncated - and a truncated token fails
   * authentication in a way that looks like an expired session.
   */
  it("survives characters that are significant in a cookie", () => {
    const token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhQGIuY29tIn0=.s1gn+ature/x";

    setStoredToken(token);

    expect(getStoredToken()).toBe(token);
  });
});

describe("clearing", () => {
  it("removes a stored token", () => {
    setStoredToken("token");
    clearStoredToken();

    expect(getStoredToken()).toBeNull();
    expect(hasStoredToken()).toBe(false);
  });

  /*
   * Sign-out runs this unconditionally, including when the session had already
   * expired and the cookie was gone.
   */
  it("is safe to call when nothing is stored", () => {
    expect(() => clearStoredToken()).not.toThrow();
    expect(getStoredToken()).toBeNull();
  });

  it("is idempotent", () => {
    setStoredToken("token");
    clearStoredToken();
    clearStoredToken();

    expect(hasStoredToken()).toBe(false);
  });
});

describe("hasStoredToken", () => {
  it("agrees with getStoredToken", () => {
    expect(hasStoredToken()).toBe(getStoredToken() !== null);

    setStoredToken("token");

    expect(hasStoredToken()).toBe(getStoredToken() !== null);
  });
});
