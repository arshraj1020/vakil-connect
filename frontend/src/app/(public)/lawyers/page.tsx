import type { Metadata } from "next";
import { Suspense } from "react";

import { LawyerCardGridSkeleton } from "@/features/lawyers/components/lawyer-card-skeleton";
import { LawyerSearchView } from "@/features/lawyers/components/lawyer-search-view";

export const metadata: Metadata = {
  title: "Find a lawyer",
  description:
    "Browse verified lawyers by practice area, city, experience and consultation fee.",
};

/**
 * Server component wrapper.
 *
 * LawyerSearchView reads the query string with useSearchParams, which requires
 * a Suspense boundary during static prerendering. Keeping the boundary here
 * lets the shell stay static while the filtered results resolve on the client.
 */
export default function LawyersPage() {
  return (
    <Suspense
      fallback={
        <div className="container space-y-6 py-8">
          <LawyerCardGridSkeleton />
        </div>
      }
    >
      <LawyerSearchView />
    </Suspense>
  );
}
