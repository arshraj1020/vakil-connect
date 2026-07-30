import { CardSkeleton } from "@/components/common/loading-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Shown while the profile segment streams in.
 *
 * Form-shaped rather than list-shaped: a profile is a small number of tall
 * cards, and a list placeholder would resettle into something quite different.
 *
 * What the user is waiting for: their account details.
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
      </div>
    </div>
  );
}
