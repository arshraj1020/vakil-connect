"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { CreditCard } from "lucide-react";
import type { ReactNode } from "react";

import { FullPageLoader } from "@/components/common/full-page-loader";
import { ROUTES } from "@/lib/routes";

import { useSubscriptionStatus } from "../hooks/use-subscription-status";

/**
 * Blocks every lawyer-section page except the subscription page itself
 * behind an active subscription.
 *
 * Rendered inside `LawyerLayout`, below `RoleGuard` - by the time this runs,
 * the visitor is already confirmed to be a lawyer, so the only question left
 * is billing status, not identity or role.
 *
 * The subscription page is explicitly exempted (by pathname, not by a prop
 * each page has to remember to pass) so an unsubscribed lawyer can always
 * reach the one page that lets them fix that - gating it too would strand
 * them with no way to pay.
 */
export function SubscriptionGate({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { data: status, isLoading } = useSubscriptionStatus();

  if (pathname === ROUTES.LAWYER_SUBSCRIPTION) {
    return <>{children}</>;
  }

  if (isLoading) {
    return <FullPageLoader label="Checking your subscription" />;
  }

  if (!status?.active) {
    return (
      <div className="grid min-h-[60vh] place-items-center px-4">
        <div className="flex max-w-sm flex-col items-center gap-4 text-center">
          <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
            <CreditCard className="size-6" aria-hidden />
          </span>

          <div className="space-y-1">
            <h1 className="text-lg font-semibold">Subscribe to continue</h1>
            <p className="text-sm text-muted-foreground">
              An active VakilConnect subscription is required to use this area. Subscribe for
              Rs 499/month or Rs 5,499/year to unlock your dashboard, appointments and profile.
            </p>
          </div>

          <Link
            href={ROUTES.LAWYER_SUBSCRIPTION}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
          >
            Go to subscription
          </Link>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
