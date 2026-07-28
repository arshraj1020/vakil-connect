import type { ReactNode } from "react";

import { RoleGuard } from "@/features/auth/components/role-guard";

/** Restricts this section to CLIENT accounts. */
export default function ClientLayout({ children }: { children: ReactNode }) {
  return <RoleGuard allow="CLIENT">{children}</RoleGuard>;
}
