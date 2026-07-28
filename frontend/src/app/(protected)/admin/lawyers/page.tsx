import type { Metadata } from "next";

import { AdminLawyerVerificationView } from "@/features/admin-lawyer-verification/components/admin-lawyer-verification-view";

export const metadata: Metadata = {
  title: "Lawyer verification",
  description: "Review and verify pending lawyer applications.",
};

/**
 * Server component: paging is held in local state rather than the query string,
 * so no Suspense boundary is needed. Access is inherited from the /admin
 * section's middleware and RoleGuard.
 */
export default function AdminLawyersPage() {
  return <AdminLawyerVerificationView />;
}
