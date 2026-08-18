import { describe, expect, it } from "vitest";

import {
  MIN_PASSWORD_LENGTH,
  forgotPasswordSchema,
  resetPasswordSchema,
} from "./password-reset-schema";

describe("forgotPasswordSchema", () => {
  it("accepts a valid address", () => {
    expect(forgotPasswordSchema.safeParse({ email: "a@b.com" }).success).toBe(
      true,
    );
  });

  it("rejects an empty or malformed address", () => {
    expect(forgotPasswordSchema.safeParse({ email: "" }).success).toBe(false);
    expect(forgotPasswordSchema.safeParse({ email: "nope" }).success).toBe(
      false,
    );
  });
});

describe("resetPasswordSchema", () => {
  const valid = {
    newPassword: "long-enough-password",
    confirmPassword: "long-enough-password",
  };

  it("accepts a matching pair at or above the minimum length", () => {
    expect(resetPasswordSchema.safeParse(valid).success).toBe(true);
  });

  it("mirrors the backend minimum length exactly", () => {
    /*
     * The backend's PasswordRules.MIN_LENGTH is authoritative and validates
     * every request regardless. This assertion exists so that if the two ever
     * drift, a test says so rather than a user discovering it at the worst
     * possible moment - mid password reset.
     */
    expect(MIN_PASSWORD_LENGTH).toBe(8);

    const tooShort = "a".repeat(MIN_PASSWORD_LENGTH - 1);
    expect(
      resetPasswordSchema.safeParse({
        newPassword: tooShort,
        confirmPassword: tooShort,
      }).success,
    ).toBe(false);

    const exactly = "a".repeat(MIN_PASSWORD_LENGTH);
    expect(
      resetPasswordSchema.safeParse({
        newPassword: exactly,
        confirmPassword: exactly,
      }).success,
    ).toBe(true);
  });

  it("rejects a mismatch and attaches the error to the confirm field", () => {
    const result = resetPasswordSchema.safeParse({
      newPassword: "long-enough-password",
      confirmPassword: "something-else-entirely",
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      // Attached to confirmPassword so it renders beside the input the user
      // must fix, not at the top of the form.
      expect(result.error.issues[0]?.path).toEqual(["confirmPassword"]);
    }
  });

  it("requires the confirmation field to be filled", () => {
    expect(
      resetPasswordSchema.safeParse({
        newPassword: "long-enough-password",
        confirmPassword: "",
      }).success,
    ).toBe(false);
  });
});
