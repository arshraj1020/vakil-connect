import type { Metadata } from "next";

import { LawyerAppointmentsView } from "@/features/lawyer-appointments/components/lawyer-appointments-view";

export const metadata: Metadata = {
  title: "Appointments",
  description: "Review requests and manage your consultations.",
};

/**
 * Server component: no query string is read, so no Suspense boundary is needed.
 * Access is inherited from the /lawyer section's middleware and RoleGuard.
 */
export default function LawyerAppointmentsPage() {
  return <LawyerAppointmentsView />;
}
