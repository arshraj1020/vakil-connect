import type { Metadata } from "next";

import { ClientDashboardView } from "@/features/client/components/client-dashboard-view";

export const metadata: Metadata = {
  title: "Dashboard",
  description: "Your consultations at a glance.",
};

/**
 * Kept as a server component so it can export metadata. The parent layout is a
 * client component, but `children` is still produced on the server and passed
 * in as a slot, so this composes cleanly.
 */
export default function ClientDashboardPage() {
  return <ClientDashboardView />;
}
