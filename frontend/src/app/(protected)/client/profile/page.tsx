import type { Metadata } from "next";

import { ClientProfileView } from "@/features/client-profile/components/client-profile-view";

export const metadata: Metadata = {
  title: "Profile",
  description: "Manage your account details.",
};

/**
 * Server component: no query string is read, so no Suspense boundary is needed.
 * Access is inherited from the /client section's middleware and RoleGuard.
 */
export default function ClientProfilePage() {
  return <ClientProfileView />;
}
