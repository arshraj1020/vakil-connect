"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { describeBookingError } from "@/features/appointments/lib/booking-errors";
import { queryKeys } from "@/lib/query-keys";
import { appointmentService } from "@/services/appointment-service";
import type { AppointmentResponse, BookAppointmentRequest } from "@/types";

/**
 * Books a consultation.
 *
 * Cache invalidation is deliberately narrow - only the two caches a booking can
 * change:
 *   appointments.client()  the client's own list
 *   dashboard.client()     the counters derived from it
 *
 * Notably NOT invalidated: lawyer search and profile caches. A booking does not
 * alter a lawyer's public data, and dropping those would force needless
 * refetches of results the user is likely to navigate back to.
 *
 * Mutations are never retried (configured globally). That matters more here
 * than anywhere else: a retried booking would race the original and surface a
 * 409 for a request that actually succeeded.
 */
export function useBookAppointment(
  options?: { onSuccess?: (appointment: AppointmentResponse) => void },
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: BookAppointmentRequest) =>
      appointmentService.bookAppointment(payload),

    onSuccess: (appointment) => {
      void queryClient.invalidateQueries({
        queryKey: queryKeys.appointments.client(),
      });
      void queryClient.invalidateQueries({
        queryKey: queryKeys.dashboard.client(),
      });

      options?.onSuccess?.(appointment);
    },

    onError: (error: unknown) => {
      /*
       * A slot conflict means the local view of taken slots is stale.
       * Refreshing the client's own appointments corrects the case where the
       * conflict was their own earlier booking; a slot taken by ANOTHER client
       * still cannot be reflected, because no endpoint exposes it.
       */
      if (describeBookingError(error).isSlotConflict) {
        void queryClient.invalidateQueries({
          queryKey: queryKeys.appointments.client(),
        });
      }
    },
  });
}
