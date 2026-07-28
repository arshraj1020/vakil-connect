"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { availabilityService } from "@/services/availability-service";
import type { AvailabilityResponse, CreateAvailabilityRequest } from "@/types";

/**
 * Mutations over the lawyer's availability.
 *
 * Invalidation is limited to `availability.mine()` - the only cache these
 * calls change. The lawyer's PUBLIC availability (keyed by lawyerId, used on
 * their profile page) is not invalidated here because this screen never loads
 * the lawyer's own id; it would refetch on next visit regardless.
 */

function useInvalidateAvailability() {
  const queryClient = useQueryClient();

  return () =>
    void queryClient.invalidateQueries({
      queryKey: queryKeys.availability.mine(),
    });
}

/** Adds a weekly window. */
export function useAddAvailability(options?: {
  onSuccess?: (window: AvailabilityResponse) => void;
  onError?: (error: unknown) => void;
}) {
  const invalidate = useInvalidateAvailability();

  return useMutation({
    mutationFn: (payload: CreateAvailabilityRequest) =>
      availabilityService.addAvailability(payload),
    onSuccess: (window) => {
      invalidate();
      options?.onSuccess?.(window);
    },
    onError: (error) => options?.onError?.(error),
  });
}

/** Removes a window. */
export function useRemoveAvailability(options?: {
  onSuccess?: () => void;
  onError?: (error: unknown) => void;
}) {
  const invalidate = useInvalidateAvailability();

  return useMutation({
    mutationFn: (availabilityId: string) =>
      availabilityService.removeAvailability(availabilityId),
    onSuccess: () => {
      invalidate();
      options?.onSuccess?.();
    },
    onError: (error) => {
      // The window may already be gone; refetch so the list reflects reality.
      invalidate();
      options?.onError?.(error);
    },
  });
}

/**
 * Edits a window.
 *
 * The API has no update endpoint, so this composes create and delete. The
 * ORDER is the important part:
 *
 *   create new -> delete old
 *
 * Failure modes under this ordering:
 *   create fails -> nothing changed; the original window is intact
 *   delete fails -> a duplicate exists, which is visible and removable
 *
 * The reverse order (delete then create) would DESTROY the window whenever the
 * create failed - a validation error, a duplicate, or a dropped connection
 * would silently erase the lawyer's hours. That risk is not acceptable for a
 * cosmetic gain in atomicity, which neither ordering actually provides.
 */
export function useUpdateAvailability(options?: {
  onSuccess?: (window: AvailabilityResponse) => void;
  onError?: (error: unknown) => void;
  /** The replacement was created but the old window could not be removed. */
  onPartialSuccess?: (window: AvailabilityResponse) => void;
}) {
  const invalidate = useInvalidateAvailability();

  return useMutation({
    mutationFn: async ({
      previousId,
      payload,
    }: {
      previousId: string;
      payload: CreateAvailabilityRequest;
    }): Promise<{ window: AvailabilityResponse; removedPrevious: boolean }> => {
      const window = await availabilityService.addAvailability(payload);

      try {
        await availabilityService.removeAvailability(previousId);
        return { window, removedPrevious: true };
      } catch {
        // The replacement exists, so nothing is lost - report it and let the
        // lawyer delete the leftover themselves.
        return { window, removedPrevious: false };
      }
    },

    onSuccess: ({ window, removedPrevious }) => {
      invalidate();

      if (removedPrevious) options?.onSuccess?.(window);
      else options?.onPartialSuccess?.(window);
    },

    onError: (error) => options?.onError?.(error),
  });
}
