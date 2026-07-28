"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { AvailabilityResponse, DayOfWeek } from "@/types";
import type { AvailabilityWindowFormValues } from "@/features/lawyer-availability/schemas/availability-schema";

import { TimeWindowEditor } from "./time-window-editor";

/**
 * Add or edit a window.
 *
 * One dialog for both: the form is identical, only the copy and the submit
 * handler differ. `editingWindow` being non-null switches it to edit mode.
 *
 * Keyed on the target so React remounts the editor when it changes - without
 * that, the form would keep the previous window's values.
 */
export function TimeWindowDialog({
  open,
  onOpenChange,
  editingWindow,
  defaultDay,
  existingWindows,
  isPending,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Null when adding. */
  editingWindow: AvailabilityResponse | null;
  /** Preselected day when adding from a specific day's card. */
  defaultDay?: DayOfWeek;
  existingWindows: AvailabilityResponse[];
  isPending: boolean;
  onSubmit: (values: AvailabilityWindowFormValues) => void;
}) {
  const isEditing = editingWindow !== null;

  return (
    <Dialog open={open} onOpenChange={isPending ? undefined : onOpenChange}>
      <DialogContent className="max-w-md" showClose={!isPending}>
        <DialogHeader>
          <DialogTitle>
            {isEditing ? "Edit consultation hours" : "Add consultation hours"}
          </DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update when you are available on this day."
              : "Set a weekly window when clients can book consultations."}
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4">
          <TimeWindowEditor
            key={editingWindow?.id ?? `new-${defaultDay ?? "any"}`}
            defaultValues={
              editingWindow
                ? {
                    dayOfWeek: editingWindow.dayOfWeek,
                    startTime: editingWindow.startTime,
                    endTime: editingWindow.endTime,
                  }
                : { dayOfWeek: defaultDay }
            }
            existingWindows={existingWindows}
            editingWindowId={editingWindow?.id}
            isPending={isPending}
            submitLabel={isEditing ? "Save changes" : "Add hours"}
            onSubmit={onSubmit}
            onCancel={() => onOpenChange(false)}
          />
        </div>
      </DialogContent>
    </Dialog>
  );
}
