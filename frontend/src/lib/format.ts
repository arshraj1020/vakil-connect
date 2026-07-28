/**
 * Value formatting shared across features.
 *
 * The locale and currency are fixed rather than read from the browser: prices
 * are set by lawyers in INR, so rendering them in a visitor's local currency
 * symbol would misrepresent the amount. A fixed locale is also deterministic
 * between server and client, which avoids hydration mismatches.
 */

const currencyFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

const compactNumberFormatter = new Intl.NumberFormat("en-IN");

/** "₹1,500" */
export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount);
}

/** "1,234" */
export function formatNumber(value: number): string {
  return compactNumberFormatter.format(value);
}

/** "4.5" - ratings arrive rounded to 2 decimals but usually read better at 1. */
export function formatRating(rating: number): string {
  return rating.toFixed(1);
}

/** "5 years" / "1 year" / "New to practice" */
export function formatExperience(years: number): string {
  if (years <= 0) return "New to practice";
  return `${years} ${years === 1 ? "year" : "years"}`;
}

/** "12 reviews" / "1 review" / "No reviews yet" */
export function formatReviewCount(count: number): string {
  if (count <= 0) return "No reviews yet";
  return `${formatNumber(count)} ${count === 1 ? "review" : "reviews"}`;
}
