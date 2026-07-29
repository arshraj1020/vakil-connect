"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { useAuth } from "@/features/auth/hooks/use-auth";
import { queryKeys } from "@/lib/query-keys";
import { clientProfileService } from "@/services/client-profile-service";
import type { CurrentUserResponse, UpdateClientProfileRequest } from "@/types";

/**
 * Saves the client's account details.
 *
 * The response IS the updated record, so it is written straight into the cache
 * with `setQueryData` rather than triggering a refetch - the form can reset to
 * the server's own copy without a round-trip.
 *
 * It also pushes the record into the auth store. That is not optional: the
 * store is what the app shell reads, so without it a client who renames
 * themselves would keep seeing the OLD name in the navbar until a full reload.
 * The store is written through `useAuth`, which stays the single entry point
 * for session state.
 *
 * Nothing else is invalidated. `fullName` and `phoneNumber` appear nowhere in
 * the appointment, lawyer or review caches - a client's name is denormalised
 * into `AppointmentResponse.clientName`, but only lawyers and admins read that,
 * and neither is this user.
 */
export function useUpdateClientProfile(options?: {
  onSuccess?: (profile: CurrentUserResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();
  const { updateSessionUser } = useAuth();

  return useMutation({
    mutationFn: (payload: UpdateClientProfileRequest) =>
      clientProfileService.updateMyProfile(payload),

    onSuccess: (profile) => {
      queryClient.setQueryData(queryKeys.auth.currentUser(), profile);
      updateSessionUser(profile);

      options?.onSuccess?.(profile);
    },

    onError: (error) => options?.onError?.(error),
  });
}
