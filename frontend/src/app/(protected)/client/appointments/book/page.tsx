import type { Metadata } from "next";
import { Suspense } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { BookingView } from "@/features/appointments/components/booking-view";

export const metadata: Metadata = {
  title: "Book a consultation",
  description: "Choose a date and time for your consultation.",
};

/**
 * Server wrapper.
 *
 * BookingView reads `?lawyerId=` with useSearchParams, which needs a Suspense
 * boundary during static prerendering.
 *
 * Access control is inherited: the route sits under /client, so middleware
 * requires a session and the section's RoleGuard requires the CLIENT role -
 * matching the backend, which answers 403 on /api/client/** for other roles.
 */
export default function BookAppointmentPage() {
  return (
    <Suspense
      fallback={
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-4 lg:col-span-2">
            <Skeleton className="h-8 w-56" />
            <Skeleton className="h-80 w-full rounded-xl" />
          </div>
          <Skeleton className="h-72 w-full rounded-xl" />
        </div>
      }
    >
      <BookingView />
    </Suspense>
  );
}
