"use client";

import { CalendarClock, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useMyAvailability } from "@/features/lawyer-availability/hooks/use-my-availability";
import {
  useAddAvailability,
  useRemoveAvailability,
  useUpdateAvailability,
} from "@/features/lawyer-availability/hooks/use-update-availability";
import type { AvailabilityWindowFormValues } from "@/features/lawyer-availability/schemas/availability-schema";
import { formatDayOfWeek, formatWindow } from "@/lib/availability";
import type { DayAvailability } from "@/lib/availability";
import { isApiError, type AvailabilityResponse } from "@/types";

import { TimeWindowDialog } from "./time-window-dialog";
import { WeeklySchedule } from "./weekly-schedule";

/**
 * Availability management for the signed-in lawyer.
 *
 * Two dialogs drive every mutation: one form (add or edit) and one
 * confirmation (remove). Editing is composed from create-then-delete because
 * the API has no update endpoint - see `useUpdateAvailability` for why that
 * ordering, and not the reverse, is the safe one.
 *
 * No optimistic updates. A window can be rejected as a duplicate, and the
 * composed edit has an intermediate state where both windows exist; showing
 * either outcome before the server confirms it would be a lie the UI then has
 * to retract.
 */
export function AvailabilityView() {
  /** Non-null while the form dialog is open. `window` null means "adding". */
  const [editor, setEditor] = useState<{
    window: AvailabilityResponse | null;
    day?: DayAvailability;
  } | null>(null);

  const [removing, setRemoving] = useState<AvailabilityResponse | null>(null);

  /** The window a mutation is currently touching, so only its row goes busy. */
  const [busyWindowId, setBusyWindowId] = useState<string | null>(null);

  const { windows, days, isPending, isError, error, refetch } =
    useMyAvailability();

  const closeEditor = () => {
    setEditor(null);
    setBusyWindowId(null);
  };

  const reportError = (mutationError: unknown, fallback: string) => {
    /*
     * The backend answers 409 for both of its rules, with a message written for
     * a human ("Start time must be before end time.", "This availability slot
     * already exists."), so it is shown as-is rather than re-worded.
     */
    const description = isApiError(mutationError)
      ? mutationError.status === 404
        ? "This window no longer exists. The list has been refreshed."
        : mutationError.message
      : "Please try again.";

    toast.error(fallback, { description });
  };

  const addMutation = useAddAvailability({
    onSuccess: (window) => {
      closeEditor();
      toast.success("Consultation hours added", {
        description: `${formatDayOfWeek(window.dayOfWeek)}, ${formatWindow(window)}.`,
      });
    },
    onError: (mutationError) => {
      setBusyWindowId(null);
      reportError(mutationError, "Could not add these hours");
    },
  });

  const updateMutation = useUpdateAvailability({
    onSuccess: (window) => {
      closeEditor();
      toast.success("Consultation hours updated", {
        description: `${formatDayOfWeek(window.dayOfWeek)}, ${formatWindow(window)}.`,
      });
    },

    /*
     * The replacement was created but the original could not be deleted, so the
     * lawyer now has two windows. Nothing is lost, and the leftover is visible
     * in the list, so this is a warning with an instruction - not an error.
     */
    onPartialSuccess: (window) => {
      closeEditor();
      toast.warning("New hours added, old ones remain", {
        description: `${formatDayOfWeek(window.dayOfWeek)}, ${formatWindow(
          window,
        )} was added, but the previous window could not be removed. Please delete it below.`,
      });
    },

    onError: (mutationError) => {
      setBusyWindowId(null);
      reportError(mutationError, "Could not update these hours");
    },
  });

  const removeMutation = useRemoveAvailability({
    onSuccess: () => {
      setRemoving(null);
      setBusyWindowId(null);
      toast.success("Consultation hours removed", {
        description: "Existing appointments are not affected.",
      });
    },
    onError: (mutationError) => {
      setRemoving(null);
      setBusyWindowId(null);
      reportError(mutationError, "Could not remove these hours");
    },
  });

  const submitEditor = (values: AvailabilityWindowFormValues) => {
    if (!editor) return;

    if (editor.window) {
      setBusyWindowId(editor.window.id);
      updateMutation.mutate({ previousId: editor.window.id, payload: values });
    } else {
      addMutation.mutate(values);
    }
  };

  const confirmRemove = () => {
    if (!removing) return;

    setBusyWindowId(removing.id);
    removeMutation.mutate(removing.id);
  };

  const isEditorPending = addMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Availability"
        description="Set the weekly hours when clients can book consultations with you."
        actions={
          <Button onClick={() => setEditor({ window: null })}>
            <Plus aria-hidden />
            Add hours
          </Button>
        }
      />

      {isPending ? (
        <Card>
          <CardContent className="p-5">
            <ListSkeleton count={5} />
          </CardContent>
        </Card>
      ) : isError ? (
        <Card>
          <CardContent className="p-5">
            <ErrorState
              error={error}
              onRetry={() => void refetch()}
              title="Could not load your availability"
            />
          </CardContent>
        </Card>
      ) : windows.length === 0 ? (
        <Card>
          <CardContent className="p-5">
            <EmptyState
              icon={CalendarClock}
              title="No consultation hours yet"
              description="Clients cannot book you until you publish at least one weekly window."
              action={
                <Button onClick={() => setEditor({ window: null })}>
                  <Plus aria-hidden />
                  Add your first hours
                </Button>
              }
            />
          </CardContent>
        </Card>
      ) : (
        <WeeklySchedule
          days={days}
          busyWindowId={busyWindowId}
          onAdd={(day) => setEditor({ window: null, day })}
          onEdit={(window) => setEditor({ window })}
          onRemove={setRemoving}
        />
      )}

      <TimeWindowDialog
        open={editor !== null}
        onOpenChange={(open) => {
          if (!open) closeEditor();
        }}
        editingWindow={editor?.window ?? null}
        // Seeds the day when the lawyer adds from a specific day's card.
        defaultDay={editor?.day?.day}
        existingWindows={windows}
        isPending={isEditorPending}
        onSubmit={submitEditor}
      />

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null);
        }}
        title="Remove these consultation hours?"
        description={
          removing
            ? `${formatDayOfWeek(removing.dayOfWeek)}, ${formatWindow(
                removing,
              )} will no longer be bookable. Appointments already booked in this window are not affected.`
            : undefined
        }
        confirmLabel="Remove"
        destructive
        isPending={removeMutation.isPending}
        onConfirm={confirmRemove}
      />
    </div>
  );
}
