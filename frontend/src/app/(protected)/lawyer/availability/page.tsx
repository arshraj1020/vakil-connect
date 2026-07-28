import type { Metadata } from "next";

import { AvailabilityView } from "@/features/lawyer-availability/components/availability-view";

export const metadata: Metadata = {
  title: "Availability",
  description: "Set the weekly hours when clients can book consultations.",
};

/**
 * Server component: no query string is read, so no Suspense boundary is needed.
 * Access is inherited from the /lawyer section's middleware and RoleGuard.
 */
export default function LawyerAvailabilityPage() {
  return <AvailabilityView />;
}
