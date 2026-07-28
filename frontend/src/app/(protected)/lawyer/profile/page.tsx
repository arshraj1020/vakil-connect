import type { Metadata } from "next";

import { LawyerProfileView } from "@/features/lawyer-profile/components/lawyer-profile-view";

export const metadata: Metadata = {
  title: "Profile",
  description: "Manage your practice details and public profile.",
};

/**
 * Server component: no query string is read, so no Suspense boundary is needed.
 * Access is inherited from the /lawyer section's middleware and RoleGuard.
 */
export default function LawyerProfilePage() {
  return <LawyerProfileView />;
}
