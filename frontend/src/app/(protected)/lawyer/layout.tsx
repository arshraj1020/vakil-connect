import type { ReactNode } from "react";

import { RoleGuard } from "@/features/auth/components/role-guard";
import { SubscriptionGate } from "@/features/lawyer-subscription/components/subscription-gate";

/**
 * Restricts this section to LAWYER accounts, then to lawyers with an active
 * subscription. Order matters: SubscriptionGate assumes the visitor is
 * already a confirmed lawyer, so it sits inside RoleGuard, not beside it.
 */
export default function LawyerLayout({ children }: { children: ReactNode }) {
  return (
    <RoleGuard allow="LAWYER">
      <SubscriptionGate>{children}</SubscriptionGate>
    </RoleGuard>
  );
}
