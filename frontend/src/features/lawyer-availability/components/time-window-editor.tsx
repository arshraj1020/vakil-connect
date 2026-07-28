"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { FormRow } from "@/components/forms/form-section";
import { SubmitButton } from "@/components/forms/submit-button";
import { Button } from "@/components/ui/button";
import { DialogFooter } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  availabilityWindowSchema,
  type AvailabilityWindowFormValues,
} from "@/features/lawyer-availability/schemas/availability-schema";
import { DAY_ORDER, formatDayOfWeek } from "@/lib/availability";
import type { AvailabilityResponse, DayOfWeek } from "@/types";

/**
 * The form for one availability window, shared by add and edit.
 *
 * Native <input type="time"> is used deliberately: it emits exactly the `HH:mm`
 * the backend DTO declares via @JsonFormat, and brings the platform's own
 * keyboard handling and locale-aware display for free. A custom picker would
 * mean reimplementing both and converting formats.
 *
 * Duplicate detection needs the other windows, so it is passed in rather than
 * living in the schema - it mirrors the backend's
 * `existsByLawyerAndDayOfWeekAndStartTimeAndEndTime` check and its message.
 */
export function TimeWindowEditor({
  defaultValues,
  existingWindows,
  editingWindowId,
  isPending,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  defaultValues?: Partial<AvailabilityWindowFormValues>;
  /** Used to reject an exact duplicate before the request is made. */
  existingWindows: AvailabilityResponse[];
  /** Excluded from the duplicate check when editing, so a window can keep its own times. */
  editingWindowId?: string;
  isPending: boolean;
  submitLabel: string;
  onSubmit: (values: AvailabilityWindowFormValues) => void;
  onCancel: () => void;
}) {
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<AvailabilityWindowFormValues>({
    resolver: zodResolver(availabilityWindowSchema),
    defaultValues: {
      dayOfWeek: defaultValues?.dayOfWeek ?? "MONDAY",
      startTime: defaultValues?.startTime ?? "09:00",
      endTime: defaultValues?.endTime ?? "17:00",
    },
  });

  const submit = handleSubmit((values) => {
    const duplicate = existingWindows.some(
      (window) =>
        window.id !== editingWindowId &&
        window.dayOfWeek === values.dayOfWeek &&
        window.startTime === values.startTime &&
        window.endTime === values.endTime,
    );

    if (duplicate) {
      // Same wording the backend returns for this case (409).
      setError("startTime", {
        type: "duplicate",
        message: "This availability slot already exists",
      });
      return;
    }

    onSubmit(values);
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <FormField label="Day" error={errors.dayOfWeek?.message} required>
        {(field) => (
          <Controller
            control={control}
            name="dayOfWeek"
            render={({ field: controlled }) => (
              <Select value={controlled.value} onValueChange={controlled.onChange}>
                <SelectTrigger id={field.id} aria-invalid={field["aria-invalid"]}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DAY_ORDER.map((day: DayOfWeek) => (
                    <SelectItem key={day} value={day}>
                      {formatDayOfWeek(day)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        )}
      </FormField>

      <FormRow>
        <FormField label="From" error={errors.startTime?.message} required>
          {(field) => (
            <Controller
              control={control}
              name="startTime"
              render={({ field: controlled }) => (
                <Input
                  {...field}
                  type="time"
                  value={controlled.value}
                  onChange={controlled.onChange}
                />
              )}
            />
          )}
        </FormField>

        <FormField label="To" error={errors.endTime?.message} required>
          {(field) => (
            <Controller
              control={control}
              name="endTime"
              render={({ field: controlled }) => (
                <Input
                  {...field}
                  type="time"
                  value={controlled.value}
                  onChange={controlled.onChange}
                />
              )}
            />
          )}
        </FormField>
      </FormRow>

      <p className="text-xs text-muted-foreground">
        Clients can book any start time within this window. The closing time
        itself is not bookable.
      </p>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isPending}>
          Cancel
        </Button>
        <SubmitButton isPending={isPending} pendingLabel="Saving...">
          {submitLabel}
        </SubmitButton>
      </DialogFooter>
    </form>
  );
}
