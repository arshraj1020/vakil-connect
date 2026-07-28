import type { AdminReviewResponse } from "@/types";

/**
 * Helpers specific to review moderation.
 *
 * `hasComment` and `describeRating` are NOT here - both are needed by the
 * lawyer portal's review screen too, so they live in `lib/reviews.ts` and both
 * features import the one definition.
 *
 * Nothing in this file infers, scores or ranks. Moderation carries no backend
 * state - there is no reported flag, no severity, no reason - so anything of
 * that kind would be invented.
 */

/** Comment length past which the list clamps and offers the full text in a dialog. */
export const COMMENT_CLAMP_THRESHOLD = 180;

/**
 * Whether a comment is long enough that the card will clamp it.
 *
 * `Review.comment` allows up to 2000 characters, so a single review can be
 * longer than the viewport. Clamping keeps the page scannable; this predicate
 * lets the card offer "Read full review" only when there is genuinely more to
 * read, rather than on every row.
 */
export function isCommentClamped(review: AdminReviewResponse): boolean {
  return (review.comment?.trim().length ?? 0) > COMMENT_CLAMP_THRESHOLD;
}

/**
 * An accessible name identifying one review among many.
 *
 * The list is unordered and can contain several reviews of the same lawyer, so
 * naming a control "Delete review" alone would be ambiguous to a screen reader
 * moving button to button. Both participants and the rating disambiguate it.
 */
export function describeReview(review: AdminReviewResponse): string {
  return `${review.rating}-star review of ${review.lawyerName} by ${review.clientName}`;
}
