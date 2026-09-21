import type { Metadata } from "next";

import { SubscriptionView } from "@/features/lawyer-subscription/components/subscription-view";

export const metadata: Metadata = {
  title: "Subscription",
  description: "Manage your VakilConnect subscription.",
};

/**
 * Server component: no query string is read, so no Suspense boundary is
 * needed. Access is inherited from the /lawyer section's middleware and
 * RoleGuard, same as every sibling page here.
 */
export default function LawyerSubscriptionPage() {
  return <SubscriptionView />;
}
