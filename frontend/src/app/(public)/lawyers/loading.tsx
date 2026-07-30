import { LawyerCardGridSkeleton } from "@/features/lawyers/components/lawyer-card-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Shown while the search segment streams in.
 *
 * Reuses the feature's own `LawyerCardGridSkeleton` - the same component the
 * page's Suspense fallback already renders. The two boundaries fire at different
 * moments (this one on navigation into the segment, that one when
 * `useSearchParams` bails out of prerendering), so showing the same placeholder
 * in both makes the handover invisible rather than swapping a generic grid for a
 * lawyer-shaped one.
 *
 * The heading and filter panel are drawn too, which the Suspense fallback omits:
 * on a cold navigation there is no shell on screen yet, and results appearing
 * above an empty space would move the filters under the cursor at the moment
 * they became usable.
 *
 * What the user is waiting for: verified lawyers matching their search.
 */
export default function Loading() {
  return (
    <div className="container py-8">
      <div className="mb-6 space-y-2">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-4 w-80" />
      </div>

      <Skeleton className="mb-6 h-28 w-full rounded-xl" />

      <LawyerCardGridSkeleton />
    </div>
  );
}
