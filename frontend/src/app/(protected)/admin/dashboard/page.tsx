import type { Metadata } from "next";

import { AdminDashboardView } from "@/features/admin-dashboard/components/admin-dashboard-view";

export const metadata: Metadata = {
  title: "Admin dashboard",
  description: "Platform statistics and administrative shortcuts.",
};

/**
 * Server component: no query string is read, so no Suspense boundary is needed.
 * Access is inherited from the /admin section's middleware and RoleGuard.
 */
export default function AdminDashboardPage() {
  return <AdminDashboardView />;
}
