import type { ReactNode } from "react";

import { RoleGuard } from "@/features/auth/components/role-guard";

/** Restricts this section to LAWYER accounts. */
export default function LawyerLayout({ children }: { children: ReactNode }) {
  return <RoleGuard allow="LAWYER">{children}</RoleGuard>;
}
