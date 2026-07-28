import { Star } from "lucide-react";

import { formatRating } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * Five-star rating display.
 *
 * Read-only. Stars are decorative, so the accessible value is provided once as
 * text on the wrapper rather than announcing five separate icons.
 *
 * Partial stars are not drawn: a half-filled glyph reads as noise at this size,
 * and the numeric value beside it is already precise.
 */
export function RatingStars({
  rating,
  size = "default",
  showValue = true,
  className,
}: {
  rating: number;
  size?: "sm" | "default";
  showValue?: boolean;
  className?: string;
}) {
  const rounded = Math.round(rating);
  const starSize = size === "sm" ? "size-3" : "size-4";

  return (
    <span
      className={cn("inline-flex items-center gap-1", className)}
      aria-label={`Rated ${formatRating(rating)} out of 5`}
    >
      <span className="inline-flex items-center gap-0.5" aria-hidden>
        {Array.from({ length: 5 }, (_, index) => (
          <Star
            key={index}
            className={cn(
              starSize,
              index < rounded
                ? "fill-warning text-warning"
                : "text-muted-foreground/40",
            )}
          />
        ))}
      </span>

      {showValue ? (
        <span
          className={cn("font-medium", size === "sm" ? "text-xs" : "text-sm")}
          aria-hidden
        >
          {formatRating(rating)}
        </span>
      ) : null}
    </span>
  );
}
