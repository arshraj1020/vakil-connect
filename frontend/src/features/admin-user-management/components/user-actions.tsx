"use client";

import { Trash2, UserCheck, UserX } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { isApiError, type UserSummaryResponse } from "@/types";

import { useDeleteUser } from "../hooks/use-delete-user";
import {
  useUpdateUserStatus,
  type UserStatusAction,
} from "../hooks/use-update-user-status";
import { canDeactivate, canDelete, isSelf } from "../lib/user-utils";

/**
 * The three actions the backend supports on a user account: Activate,
 * Deactivate and Delete. There is still no Edit, Reset password, Suspend or
 * Change role control, because no such endpoint exists.
 *
 * Delete is a SEPARATE, HARSHER action from deactivate, not an alternative
 * presentation of it. Deactivate flips a flag and keeps every row - it is
 * reversible by hitting Activate. Delete is permanent: it cascades through
 * the account's lawyer profile, appointments, reviews and AI documents at the
 * database level (V10) and cannot be undone from this UI or any other. That
 * difference is why it gets its own confirmation mechanism - a plain
 * ConfirmDialog (one click past a "Cancel" default) is calibrated for
 * deactivation, not for something with no way back. Delete instead requires
 * typing the account's exact name, the same friction pattern used for
 * destroying a resource you cannot get back.
 *
 * Self-deactivation and self-deletion are both blocked as UX SAFEGUARDS, not
 * security controls - see `canDeactivate` and `canDelete` for exactly what
 * they do and do not enforce, and why. The backend enforces the real
 * invariants (own-account and last-admin) itself, returning 409.
 *
 * Owns its own mutation instances so each row tracks its own pending state.
 */
export function UserActions({
  user,
  currentUserId,
  adminCount,
  size = "sm",
  onDeleted,
}: {
  user: UserSummaryResponse;
  /** The signed-in admin, used for the self-deactivation/self-deletion guards. */
  currentUserId: string | undefined;
  /** See `canDelete` - a page-local count, not a platform-wide one. */
  adminCount: number;
  size?: "sm" | "default";
  /** Lets a parent (e.g. the details dialog) close once this row is gone. */
  onDeleted?: () => void;
}) {
  const [isConfirmingStatus, setIsConfirmingStatus] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");

  const action: UserStatusAction = user.active ? "deactivate" : "activate";
  const statusBlocked =
    action === "deactivate" && !canDeactivate(user, currentUserId);
  const deleteBlocked = !canDelete(user, currentUserId, adminCount);

  const statusMutation = useUpdateUserStatus({
    onSuccess: (updated, performed) => {
      setIsConfirmingStatus(false);

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
      setIsConfirmingStatus(false);

      toast.error("Could not update this account", {
        description: isApiError(error)
          ? error.status === 404
            ? "This account no longer exists."
            : error.message
          : "Please try again.",
      });
    },
  });

  const deleteMutation = useDeleteUser({
    onSuccess: () => {
      setIsDeleting(false);
      setDeleteConfirmText("");

      toast.success(`${user.fullName}'s account has been deleted`, {
        description:
          "Their profile, appointments, reviews and documents are gone.",
      });

      onDeleted?.();
    },

    onError: (error) => {
      toast.error("Could not delete this account", {
        // 409 covers both server-side invariants (self-delete, last admin) -
        // the backend's message is specific enough to show directly.
        description: isApiError(error)
          ? error.status === 404
            ? "This account no longer exists."
            : error.message
          : "Please try again.",
      });
    },
  });

  const StatusIcon = user.active ? UserX : UserCheck;
  const nameMatches = deleteConfirmText.trim() === user.fullName;

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant={user.active ? "outline" : "default"}
          size={size}
          disabled={statusBlocked || statusMutation.isPending}
          onClick={() => setIsConfirmingStatus(true)}
          aria-label={
            statusBlocked
              ? "You cannot deactivate your own account"
              : `${user.active ? "Deactivate" : "Activate"} ${user.fullName}`
          }
          title={
            statusBlocked ? "You cannot deactivate your own account" : undefined
          }
        >
          <StatusIcon aria-hidden />
          {user.active ? "Deactivate" : "Activate"}
        </Button>

        <Button
          variant="outline"
          size={size}
          className="text-destructive hover:text-destructive"
          disabled={deleteBlocked || deleteMutation.isPending}
          onClick={() => setIsDeleting(true)}
          aria-label={
            deleteBlocked
              ? isSelf(user, currentUserId)
                ? "You cannot delete your own account"
                : "You cannot delete the last admin account"
              : `Delete ${user.fullName}`
          }
          title={
            deleteBlocked
              ? isSelf(user, currentUserId)
                ? "You cannot delete your own account"
                : "You cannot delete the last admin account"
              : undefined
          }
        >
          <Trash2 aria-hidden />
          Delete
        </Button>
      </div>

      {isSelf(user, currentUserId) && user.active ? (
        <span className="sr-only">This is your own account.</span>
      ) : null}

      <ConfirmDialog
        open={isConfirmingStatus}
        onOpenChange={setIsConfirmingStatus}
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
        isPending={statusMutation.isPending}
        onConfirm={() => statusMutation.mutate({ userId: user.id, action })}
      />

      <Dialog
        open={isDeleting}
        onOpenChange={
          deleteMutation.isPending
            ? undefined
            : (open) => {
                setIsDeleting(open);
                if (!open) setDeleteConfirmText("");
              }
        }
      >
        <DialogContent
          className="max-w-md"
          showClose={!deleteMutation.isPending}
        >
          <DialogHeader>
            <DialogTitle>Delete {user.fullName}&apos;s account?</DialogTitle>
            <DialogDescription>
              This is permanent. Their profile, appointments, reviews and AI
              documents are all deleted with it - there is no undo. Type
              their full name to confirm.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label htmlFor="delete-confirm-name" className="sr-only">
              Type &quot;{user.fullName}&quot; to confirm
            </Label>
            <Input
              id="delete-confirm-name"
              value={deleteConfirmText}
              onChange={(event) => setDeleteConfirmText(event.target.value)}
              placeholder={user.fullName}
              disabled={deleteMutation.isPending}
              autoComplete="off"
            />
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setIsDeleting(false);
                setDeleteConfirmText("");
              }}
              disabled={deleteMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={!nameMatches || deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(user.id)}
            >
              {deleteMutation.isPending ? <Spinner size="sm" /> : null}
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
