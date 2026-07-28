"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { appointmentService } from "@/services/appointment-service";
import type { AppointmentResponse } from "@/types";

/**
 * Cancels an appointment.
 *
 * No optimistic cache update, deliberately. A cancel can legitimately fail with
 * 409 - the lawyer may have completed or rejected it moments earlier - and
 * rolling a row back from "Cancelled" to "Confirmed" is more disorienting than
 * a brief pending state. The caller tracks which row is in flight and shows
 * that instead.
 *
 * Invalidation is limited to the two caches a cancel can change: the client's
 * list and the dashboard counters derived from it.
 */
export function useCancelAppointment(options?: {
  onSuccess?: (appointment: AppointmentResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (appointmentId: string) =>
      appointmentService.cancelAppointment(appointmentId),

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
       * Whatever the reason, the local view is now out of date: a 409 means the
       * status changed underneath us, a 404 means it is gone. Refetching lets
       * the row settle into its true state rather than leaving a stale action
       * available.
       */
      void queryClient.invalidateQueries({
        queryKey: queryKeys.appointments.client(),
      });

      options?.onError?.(error);
    },
  });
}
