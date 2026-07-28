import type { Metadata } from "next";

import { AdminUserManagementView } from "@/features/admin-user-management/components/admin-user-management-view";

export const metadata: Metadata = {
  title: "User management",
  description: "Review accounts and control who can sign in.",
};

/**
 * Server component: paging and the role filter are held in local state rather
 * than the query string, so no Suspense boundary is needed. Access is inherited
 * from the /admin section's middleware and RoleGuard.
 */
export default function AdminUsersPage() {
  return <AdminUserManagementView />;
}
