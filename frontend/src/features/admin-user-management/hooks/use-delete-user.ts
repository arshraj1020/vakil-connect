"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { adminUserService } from "@/services/admin-user-service";

/**
 * Permanently deletes one user account.
 *
 * No optimistic update - deletion is irreversible, so the row must not
 * disappear from the list until the server has actually confirmed it cascaded
 * successfully.
 *
 * Invalidates the same `admin.users` prefix as `useUpdateUserStatus`, plus
 * `dashboard.admin()`: unlike a status flip, a delete changes the analytics
 * counts (totalUsers, and possibly totalLawyers / verifiedLawyers if the
 * deleted account was a lawyer).
 *
 * There is no bulk variant because the API has no bulk endpoint.
 */
export function useDeleteUser(options?: {
  onSuccess?: () => void;
  onError?: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) => adminUserService.deleteUser(userId),

    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: [...queryKeys.admin.all, "users"],
      });

      void queryClient.invalidateQueries({
        queryKey: queryKeys.dashboard.admin(),
      });

      options?.onSuccess?.();
    },

    onError: (error) => options?.onError?.(error),
  });
}
