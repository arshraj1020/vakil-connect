import { ListSkeleton } from "@/components/common/loading-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Shown while the segment streams in. The placeholder matches the real
 * layout so the page does not jump once data arrives.
 *
 * What the user is waiting for: your published consulting hours.
 */
export default function Loading() {
  return (
    <div>
      <div className="mb-6 space-y-2">
        <Skeleton className="h-8 w-52" />
        <Skeleton className="h-4 w-72" />
      </div>
      <ListSkeleton count={5} />
    </div>
  );
}
