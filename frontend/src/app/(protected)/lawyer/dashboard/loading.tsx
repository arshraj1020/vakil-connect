import { ListSkeleton, StatCardSkeleton } from "@/components/common/loading-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Shown while the dashboard segment streams in.
 *
 * Mirrors the real layout - a row of summary figures above a recent-activity
 * list - so the page settles into place instead of rearranging itself. What the
 * user is waiting for: your practice at a glance.
 */
export default function Loading() {
  return (
    <div>
      <div className="mb-6 space-y-2">
        <Skeleton className="h-8 w-52" />
        <Skeleton className="h-4 w-72" />
      </div>
      <StatCardSkeleton />
      <ListSkeleton className="mt-6" count={4} />
    </div>
  );
}
