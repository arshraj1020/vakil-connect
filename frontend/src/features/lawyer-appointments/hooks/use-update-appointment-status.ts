"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import {
  appointmentService,
  type LawyerAppointmentAction,
} from "@/services/appointment-service";
import type { AppointmentResponse } from "@/types";

export interface StatusChangeVariables {
  appointmentId: string;
  action: LawyerAppointmentAction;
}

/**
 * Accepts, rejects or completes an appointment.
 *
 * No optimistic update, deliberately. Every one of these transitions can
 * genuinely conflict - a client may cancel while the lawyer is deciding, giving
 * a 409 - and flipping a badge to "Confirmed" only to revert it to "Cancelled"
 * is more disorienting than a brief pending state on the row.
 *
 * Invalidation is limited to appointment-related caches:
 *   appointments.lawyer()  the list being acted on
 *   dashboard.lawyer()     the counters derived from it
 *
 * Notably NOT invalidated: the client's caches (a different user's data, not in
 * this cache) or any lawyer search/profile entry, which a status change does
 * not affect.
 */
export function useUpdateAppointmentStatus(options?: {
  onSuccess?: (appointment: AppointmentResponse, action: LawyerAppointmentAction) => void;
  onError?: (error: unknown, action: LawyerAppointmentAction) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ appointmentId, action }: StatusChangeVariables) =>
      appointmentService.updateLawyerAppointmentStatus(appointmentId, action),

    onSuccess: (appointment, variables) => {
      void queryClient.invalidateQueries({
        queryKey: queryKeys.appointments.lawyer(),
      });
      void queryClient.invalidateQueries({
        queryKey: queryKeys.dashboard.lawyer(),
      });

      options?.onSuccess?.(appointment, variables.action);
    },

    onError: (error, variables) => {
      /*
       * A 409 means the status changed underneath us; a 404 means it is gone.
       * Either way the local list is stale, so refetch and let the row settle
       * into its true state rather than leaving an invalid action on screen.
       */
      void queryClient.invalidateQueries({
        queryKey: queryKeys.appointments.lawyer(),
      });

      options?.onError?.(error, variables.action);
    },
  });
}
