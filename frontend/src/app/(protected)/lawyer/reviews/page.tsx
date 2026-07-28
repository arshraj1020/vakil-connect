import type { Metadata } from "next";

import { LawyerReviewsView } from "@/features/lawyer-reviews/components/lawyer-reviews-view";

export const metadata: Metadata = {
  title: "Reviews",
  description: "Client feedback from your completed consultations.",
};

/**
 * Server component: paging is held in local state rather than the query string,
 * so no Suspense boundary is needed. Access is inherited from the /lawyer
 * section's middleware and RoleGuard.
 */
export default function LawyerReviewsPage() {
  return <LawyerReviewsView />;
}
