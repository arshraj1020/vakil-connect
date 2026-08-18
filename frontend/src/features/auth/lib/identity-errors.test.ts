import { describe, expect, it } from "vitest";

import { ApiError } from "@/types";

import {
  classifyTokenError,
  isCooldownActive,
  isEmailNotVerified,
} from "./identity-errors";

/**
 * The decision layer for every token screen.
 *
 * Pure functions over an error, so they are tested without rendering a
 * component or mocking a router - which is also why they were extracted.
 */
describe("classifyTokenError", () => {
  it("maps TOKEN_EXPIRED to an expired outcome that offers a new link", () => {
    const result = classifyTokenError(
      new ApiError(410, "This link has expired.", null, null, "TOKEN_EXPIRED"),
    );

    expect(result.outcome).toBe("expired");
    expect(result.offerNewLink).toBe(true);
  });

  it("maps TOKEN_ALREADY_USED without offering a new link", () => {
    // A used link usually means it worked the first time, so the useful action
    // is signing in - not emailing another link nobody needs.
    const result = classifyTokenError(
      new ApiError(409, "Already used.", null, null, "TOKEN_ALREADY_USED"),
    );

    expect(result.outcome).toBe("already-used");
    expect(result.offerNewLink).toBe(false);
  });

  it("maps TOKEN_INVALID to an invalid outcome", () => {
    const result = classifyTokenError(
      new ApiError(400, "Invalid.", null, null, "TOKEN_INVALID"),
    );

    expect(result.outcome).toBe("invalid");
    expect(result.offerNewLink).toBe(true);
  });

  it("treats a network failure as recoverable and does NOT burn the link", () => {
    // status 0 = no response reached the browser. The token was never
    // presented, so it is still good and a replacement would be wasted.
    const result = classifyTokenError(new ApiError(0, "Offline"));

    expect(result.outcome).toBe("network");
    expect(result.offerNewLink).toBe(false);
  });

  it("falls back to unknown for an unrecognised code", () => {
    const result = classifyTokenError(
      new ApiError(500, "Boom", null, null, "SOMETHING_NEW"),
    );

    expect(result.outcome).toBe("unknown");
  });

  it("falls back to unknown for a non-ApiError", () => {
    expect(classifyTokenError(new Error("nope")).outcome).toBe("unknown");
    expect(classifyTokenError(undefined).outcome).toBe("unknown");
  });

  it("branches on code, never on message", () => {
    /*
     * The guard that matters. Message text is user-facing prose that a copy
     * edit is free to reword; if control flow depended on it, rewording a
     * sentence would silently change behaviour.
     */
    const misleading = new ApiError(
      410,
      "This link has already been used.",
      null,
      null,
      "TOKEN_EXPIRED",
    );

    expect(classifyTokenError(misleading).outcome).toBe("expired");
  });
});

describe("isEmailNotVerified", () => {
  it("is true only for the EMAIL_NOT_VERIFIED code", () => {
    expect(
      isEmailNotVerified(
        new ApiError(403, "Verify first", null, null, "EMAIL_NOT_VERIFIED"),
      ),
    ).toBe(true);
  });

  it("is false for a plain 403 with no code", () => {
    // A role-authorisation 403 must not be mistaken for an unverified account,
    // or the login form would show a resend panel to the wrong person.
    expect(isEmailNotVerified(new ApiError(403, "Access denied"))).toBe(false);
  });

  it("is false for a 401 credential failure", () => {
    expect(isEmailNotVerified(new ApiError(401, "Bad credentials"))).toBe(false);
  });

  it("is false for a non-ApiError", () => {
    expect(isEmailNotVerified(new Error("boom"))).toBe(false);
  });
});

describe("isCooldownActive", () => {
  it("is true only for COOLDOWN_ACTIVE", () => {
    expect(
      isCooldownActive(
        new ApiError(429, "Wait", null, null, "COOLDOWN_ACTIVE"),
      ),
    ).toBe(true);
  });

  it("is false for a 429 without the code", () => {
    // A future rate limiter also returns 429; only the cooldown should render
    // the "we already sent one" copy.
    expect(isCooldownActive(new ApiError(429, "Too many requests"))).toBe(false);
  });
});
