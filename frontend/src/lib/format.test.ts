import { describe, expect, it } from "vitest";

import {
  formatCurrency,
  formatExperience,
  formatNumber,
  formatRating,
  formatReviewCount,
} from "@/lib/format";

/**
 * Display formatting.
 *
 * Two things are worth pinning: the pluralisation branches, and the zero cases,
 * where the honest string is words rather than "0". Everything reaches a user
 * directly, and "1 years" or "0 reviews" is the kind of defect that survives to
 * production because no test ever passed one.
 *
 * The Intl assertions use `toContain` for digits rather than matching the whole
 * string: the grouping separator and the space after the currency symbol are
 * ICU's business and vary by Node version, so asserting them exactly would make
 * this suite fail on an upgrade that changed nothing users care about.
 */

describe("formatCurrency", () => {
  it("renders rupees without decimal noise", () => {
    const output = formatCurrency(1500);

    expect(output).toContain("1,500");
    expect(output).toContain("₹");
    expect(output).not.toContain(".00");
  });

  it("handles zero", () => {
    expect(formatCurrency(0)).toContain("0");
  });
});

describe("formatNumber", () => {
  it("groups thousands", () => {
    expect(formatNumber(1234)).toContain("1,234");
  });

  it("leaves small numbers alone", () => {
    expect(formatNumber(7)).toBe("7");
  });
});

describe("formatRating", () => {
  it("always shows one decimal place", () => {
    expect(formatRating(4)).toBe("4.0");
    expect(formatRating(4.5)).toBe("4.5");
  });

  /* Ratings arrive rounded to two decimals from the backend average. */
  it("rounds a two-decimal rating to one", () => {
    expect(formatRating(4.25)).toBe("4.3");
    expect(formatRating(4.24)).toBe("4.2");
  });

  it("renders an unrated lawyer as 0.0", () => {
    expect(formatRating(0)).toBe("0.0");
  });
});

describe("formatExperience", () => {
  it("uses the singular for exactly one year", () => {
    expect(formatExperience(1)).toBe("1 year");
  });

  it("uses the plural for more than one", () => {
    expect(formatExperience(5)).toBe("5 years");
  });

  /* The backend permits 0 (@Min(0)), so this branch is reachable. */
  it("says something meaningful at zero rather than '0 years'", () => {
    expect(formatExperience(0)).toBe("New to practice");
  });

  it("treats a negative as zero rather than rendering it", () => {
    expect(formatExperience(-3)).toBe("New to practice");
  });
});

describe("formatReviewCount", () => {
  it("uses the singular for exactly one review", () => {
    expect(formatReviewCount(1)).toBe("1 review");
  });

  it("uses the plural and groups large counts", () => {
    expect(formatReviewCount(12)).toBe("12 reviews");
    expect(formatReviewCount(1234)).toBe("1,234 reviews");
  });

  it("says 'No reviews yet' rather than '0 reviews'", () => {
    expect(formatReviewCount(0)).toBe("No reviews yet");
  });
});
