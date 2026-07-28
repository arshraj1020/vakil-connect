import type { ReactNode } from "react";

import { RoleGuard } from "@/features/auth/components/role-guard";

/** Restricts this section to ADMIN accounts. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <RoleGuard allow="ADMIN">{children}</RoleGuard>;
}
