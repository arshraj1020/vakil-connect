import { CardSkeleton } from "@/components/common/loading-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Shown while the profile segment streams in.
 *
 * Three cards, matching the real form's sections (contact, practice details,
 * biography) rather than a generic block.
 *
 * What the user is waiting for: their public profile and practice details.
 */
export default function Loading() {
  return (
    <div>
      <div className="mb-6 space-y-2">
        <Skeleton className="h-8 w-52" />
        <Skeleton className="h-4 w-72" />
      </div>

      <div className="space-y-6">
        <CardSkeleton />
        <CardSkeleton />
        <CardSkeleton />
      </div>
    </div>
  );
}
