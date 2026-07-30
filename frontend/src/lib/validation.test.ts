import { describe, expect, it } from "vitest";

import { PHONE_PATTERN, optionalPhoneNumberSchema } from "@/lib/validation";

/**
 * Shared validation rules.
 *
 * These exist so registration and both profile forms agree on what a phone
 * number is. The value of testing them is that a change here silently changes
 * three forms at once.
 *
 * The schema mirrors the backend's Bean Validation `@Pattern`; where the two
 * disagree the user sees a server error on a field the client already accepted,
 * which is the failure these assertions are shaped to catch.
 */

describe("PHONE_PATTERN", () => {
  it.each([
    ["ten digits", "9876543210"],
    ["fifteen digits", "123456789012345"],
    ["leading plus", "+919876543210"],
  ])("accepts %s", (_label, value) => {
    expect(PHONE_PATTERN.test(value)).toBe(true);
  });

  it.each([
    ["nine digits", "987654321"],
    ["sixteen digits", "1234567890123456"],
    ["spaces", "98765 43210"],
    ["hyphens", "98765-43210"],
    ["parentheses", "(98)7654321"],
    ["letters", "98765abcde"],
    ["a plus that is not leading", "9876+43210"],
    ["empty", ""],
  ])("rejects %s", (_label, value) => {
    expect(PHONE_PATTERN.test(value)).toBe(false);
  });
});

describe("optionalPhoneNumberSchema", () => {
  it("accepts a valid number", () => {
    expect(optionalPhoneNumberSchema.safeParse("9876543210").success).toBe(true);
  });

  /**
   * Empty is valid HERE and the caller must then omit the key entirely. The
   * backend's `@Pattern` passes `null` but rejects `""`, so submitting the empty
   * string fails server-side on a field the user deliberately left blank.
   */
  it("accepts empty, because the field is optional", () => {
    expect(optionalPhoneNumberSchema.safeParse("").success).toBe(true);
  });

  it("trims before validating, so whitespace-only is treated as empty", () => {
    const result = optionalPhoneNumberSchema.safeParse("   ");

    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toBe("");
  });

  it("trims surrounding whitespace off a real number", () => {
    const result = optionalPhoneNumberSchema.safeParse("  9876543210  ");

    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toBe("9876543210");
  });

  it("rejects a malformed number with a usable message", () => {
    const result = optionalPhoneNumberSchema.safeParse("98765");

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0]?.message).toBe("Enter a valid phone number");
    }
  });
});
