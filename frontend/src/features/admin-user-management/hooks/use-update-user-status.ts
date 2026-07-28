"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { adminUserService } from "@/services/admin-user-service";
import type { UserSummaryResponse } from "@/types";

/** Which endpoint to call. The value doubles as the verb shown to the user. */
export type UserStatusAction = "activate" | "deactivate";

/**
 * Activates or deactivates one account.
 *
 * No optimistic update. Both transitions are cheap to get wrong - showing an
 * account as blocked when the request failed would be worse than a brief
 * pending state on the affected row - and the endpoint returns the updated user
 * anyway, so the confirmed state arrives with the response.
 *
 * Invalidation is limited to user management, as required:
 *
 *   admin.users prefix   every cached page and role filter, since a status
 *                        change is visible in all of them
 *
 * Deliberately NOT invalidated: `dashboard.admin()` (analytics counts users by
 * ROLE only and reports no active/inactive figures, so nothing there changes),
 * nor lawyers, appointments, reviews or availability - none of which a status
 * flag touches. This is a genuinely self-contained mutation.
 *
 * There is no bulk variant because the API has no bulk endpoint; looping N PUTs
 * behind one button would give N failure points and no honest partial result.
 */
export function useUpdateUserStatus(options?: {
  onSuccess?: (user: UserSummaryResponse, action: UserStatusAction) => void;
  onError?: (error: unknown, action: UserStatusAction) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      userId,
      action,
    }: {
      userId: string;
      action: UserStatusAction;
    }) =>
      action === "activate"
        ? adminUserService.activateUser(userId)
        : adminUserService.deactivateUser(userId),

    onSuccess: (user, { action }) => {
      void queryClient.invalidateQueries({
        queryKey: [...queryKeys.admin.all, "users"],
      });

      options?.onSuccess?.(user, action);
    },

    onError: (error, { action }) => options?.onError?.(error, action),
  });
}
