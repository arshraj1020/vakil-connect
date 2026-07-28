import { InitialsAvatar } from "@/components/common/initials-avatar";
import { RatingStars } from "@/components/common/rating-stars";
import { formatTimestamp } from "@/lib/date";
import type { ReviewResponse } from "@/types";

/**
 * A single published review.
 *
 * Only the reviewer's display name is available - the API deliberately does not
 * expose the client's id on a public endpoint - so the avatar is built from the
 * name.
 *
 * `comment` is optional: a client may leave a rating with no words.
 */
export function ReviewCard({ review }: { review: ReviewResponse }) {
  return (
    <article className="flex gap-3 py-4 first:pt-0 last:pb-0">
      <InitialsAvatar name={review.clientName} size="sm" />

      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <p className="text-sm font-medium">{review.clientName}</p>
          <RatingStars rating={review.rating} size="sm" showValue={false} />
          <time
            dateTime={review.createdAt}
            className="text-xs text-muted-foreground"
          >
            {formatTimestamp(review.createdAt)}
          </time>
        </div>

        {review.comment ? (
          <p className="text-sm leading-relaxed text-muted-foreground">
            {review.comment}
          </p>
        ) : null}
      </div>
    </article>
  );
}
