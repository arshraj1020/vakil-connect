import type { Metadata } from "next";

import { AdminReviewModerationView } from "@/features/admin-review-moderation/components/admin-review-moderation-view";

export const metadata: Metadata = {
  title: "Review moderation",
  description: "Review client feedback and remove anything that breaches the guidelines.",
};

/**
 * Server component: paging is held in local state rather than the query string,
 * so no Suspense boundary is needed. Access is inherited from the /admin
 * section's middleware and RoleGuard.
 */
export default function AdminReviewsPage() {
  return <AdminReviewModerationView />;
}
