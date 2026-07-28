"use client";

import { AlertCircle } from "lucide-react";

import { SubmitButton } from "@/components/forms/submit-button";
import { Button } from "@/components/ui/button";

/**
 * Sticky save/discard bar, shown only when there is something to save.
 *
 * Sticky because the form is longer than a viewport: a save button below the
 * fold means editing the fee near the top requires scrolling past the bio to
 * commit it. Hidden entirely when the form is pristine, so a lawyer who is only
 * reading their profile is not shown a control that would do nothing.
 *
 * `role="status"` announces its appearance once, which is the notification a
 * screen-reader user needs ("You have unsaved changes"); a live region on the
 * buttons themselves would be noise.
 */
export function SaveChangesBar({
  isDirty,
  isPending,
  onCancel,
}: {
  isDirty: boolean;
  isPending: boolean;
  onCancel: () => void;
}) {
  if (!isDirty) return null;

  return (
    <div className="sticky bottom-4 z-10">
      <div
        role="status"
        className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-background/95 p-3 shadow-lg backdrop-blur"
      >
        <p className="inline-flex items-center gap-2 text-sm text-muted-foreground">
          <AlertCircle className="size-4 text-warning" aria-hidden />
          You have unsaved changes
        </p>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={onCancel}
            disabled={isPending}
          >
            Discard
          </Button>

          <SubmitButton isPending={isPending} pendingLabel="Saving...">
            Save changes
          </SubmitButton>
        </div>
      </div>
    </div>
  );
}
