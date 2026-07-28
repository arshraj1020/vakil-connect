import type { Metadata } from "next";

import { LawyerDashboardView } from "@/features/lawyer-dashboard/components/lawyer-dashboard-view";

export const metadata: Metadata = {
  title: "Dashboard",
  description: "Your practice at a glance.",
};

/**
 * Server component, so metadata can be exported. Access is inherited: the route
 * sits under /lawyer, which middleware gates on a session and the section's
 * RoleGuard gates on the LAWYER role.
 */
export default function LawyerDashboardPage() {
  return <LawyerDashboardView />;
}
