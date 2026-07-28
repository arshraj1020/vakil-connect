/**
 * Review helpers shared across features.
 *
 * The lawyer portal reads `ReviewResponse` and the admin portal reads
 * `AdminReviewResponse`. The two DTOs differ - only the admin one names the
 * lawyer - but they agree on the fields these helpers touch, so the helpers are
 * typed structurally rather than against either DTO. One definition, both
 * callers, and a third DTO would work without change.
 *
 * Lives beside `lib/status.ts` and `lib/availability.ts`, the other cross-feature
 * domain helpers.
 */

/**
 * Whether the client wrote anything beyond the star rating.
 *
 * `comment` is nullable in both DTOs - `CreateReviewRequest` requires a rating
 * but not a comment - so a rating with no words is a valid review, not a broken
 * one, and callers must render its absence rather than an empty line.
 */
export function hasComment(review: { comment: string | null }): boolean {
  return typeof review.comment === "string" && review.comment.trim().length > 0;
}

/**
 * A rating in words, for accessible names.
 *
 * `RatingStars` already announces "Rated 4 out of 5" on its own wrapper, so this
 * is for places where the stars are decorative inside a larger control that
 * needs a single accessible name identifying the reviewer too.
 */
export function describeRating(rating: number): string {
  return `${rating} out of 5 stars`;
}
