import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Placeholder matching LawyerCard's layout.
 *
 * Shape-matched rather than a generic block, so results replacing skeletons do
 * not shift the grid.
 */
export function LawyerCardSkeleton() {
  return (
    <Card>
      <CardContent className="space-y-4 p-5">
        <div className="flex items-start gap-3">
          <Skeleton className="size-12 shrink-0 rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-1/2" />
          </div>
        </div>

        <Skeleton className="h-4 w-24" />

        <div className="flex gap-1.5">
          <Skeleton className="h-5 w-20 rounded-full" />
          <Skeleton className="h-5 w-24 rounded-full" />
        </div>

        <div className="border-t border-border pt-3">
          <Skeleton className="h-5 w-28" />
        </div>
      </CardContent>
    </Card>
  );
}

export function LawyerCardGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }, (_, index) => (
        <LawyerCardSkeleton key={index} />
      ))}
    </div>
  );
}
