"use client";

import { PageHeader } from "@/components/common/page-header";
import { useAuth } from "@/features/auth/hooks/use-auth";

/**
 * Greeting for the dashboard.
 *
 * No time-of-day greeting ("Good morning"): computing it during render risks a
 * server/client hydration mismatch, and the value it adds is small.
 */
export function WelcomeHeader() {
  const { user } = useAuth();
  const firstName = user?.fullName.split(" ")[0] ?? "";

  return (
    <PageHeader
      title={firstName ? `Welcome back, ${firstName}` : "Welcome back"}
      description="Here is an overview of your consultations."
    />
  );
}
