"use client";

import { UserCheck, UserX } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { Button } from "@/components/ui/button";
import { isApiError, type UserSummaryResponse } from "@/types";

import {
  useUpdateUserStatus,
  type UserStatusAction,
} from "../hooks/use-update-user-status";
import { canDeactivate, isSelf } from "../lib/user-utils";

/**
 * The only two actions the backend supports on a user account.
 *
 * There is no Edit, Delete, Reset password, Suspend or Change role control,
 * because no such endpoint exists - the admin API offers exactly
 * `PUT .../activate` and `PUT .../deactivate` and nothing else.
 *
 * Self-deactivation is blocked as a UX SAFEGUARD, not a security control - the
 * check runs in the browser and does not stop a direct API call. It exists
 * because `setUserActive` applies no guard and authentication reads
 * `.disabled(!active)`, so an admin who deactivated their own account would be
 * locked out at their next sign-in with no way to undo it. The button renders
 * disabled with the reason on it rather than hidden, so the rule reads as a
 * rule instead of a missing feature. See `canDeactivate` for the server-side
 * invariants this does NOT enforce.
 *
 * Owns its own mutation instance so each row tracks its own pending state.
 */
export function UserActions({
  user,
  currentUserId,
  size = "sm",
}: {
  user: UserSummaryResponse;
  /** The signed-in admin, used only for the self-deactivation guard. */
  currentUserId: string | undefined;
  size?: "sm" | "default";
}) {
  const [isConfirming, setIsConfirming] = useState(false);

  const action: UserStatusAction = user.active ? "deactivate" : "activate";
  const blocked = action === "deactivate" && !canDeactivate(user, currentUserId);

  const mutation = useUpdateUserStatus({
    onSuccess: (updated, performed) => {
      setIsConfirming(false);

      toast.success(
        performed === "activate"
          ? `${updated.fullName} can sign in again`
          : `${updated.fullName} has been deactivated`,
        {
          description:
            performed === "activate"
              ? "Their account is active."
              : "Their access has been revoked immediately.",
        },
      );
    },

    onError: (error) => {
      setIsConfirming(false);

      toast.error("Could not update this account", {
        description: isApiError(error)
          ? error.status === 404
            ? "This account no longer exists."
            : error.message
          : "Please try again.",
      });
    },
  });

  const Icon = user.active ? UserX : UserCheck;

  return (
    <>
      <Button
        variant={user.active ? "outline" : "default"}
        size={size}
        disabled={blocked || mutation.isPending}
        onClick={() => setIsConfirming(true)}
        aria-label={
          blocked
            ? "You cannot deactivate your own account"
            : `${user.active ? "Deactivate" : "Activate"} ${user.fullName}`
        }
        title={blocked ? "You cannot deactivate your own account" : undefined}
      >
        <Icon aria-hidden />
        {user.active ? "Deactivate" : "Activate"}
      </Button>

      {isSelf(user, currentUserId) && user.active ? (
        <span className="sr-only">This is your own account.</span>
      ) : null}

      <ConfirmDialog
        open={isConfirming}
        onOpenChange={setIsConfirming}
        title={
          user.active
            ? `Deactivate ${user.fullName}?`
            : `Activate ${user.fullName}?`
        }
        description={
          user.active
            ? "This will block the account immediately, including any session that is currently open."
            : "This will restore the account's ability to sign in."
        }
        confirmLabel={user.active ? "Deactivate" : "Activate"}
        destructive={user.active}
        isPending={mutation.isPending}
        onConfirm={() => mutation.mutate({ userId: user.id, action })}
      />
    </>
  );
}
