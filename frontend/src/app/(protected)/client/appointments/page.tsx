import type { Metadata } from "next";

import { ClientAppointmentsView } from "@/features/appointments/components/client-appointments-view";

export const metadata: Metadata = {
  title: "My appointments",
  description: "Track your consultation requests and history.",
};

/**
 * Server component: no query string is read here, so no Suspense boundary is
 * needed - unlike the booking page, which reads `?lawyerId=`.
 */
export default function ClientAppointmentsPage() {
  return <ClientAppointmentsView />;
}
