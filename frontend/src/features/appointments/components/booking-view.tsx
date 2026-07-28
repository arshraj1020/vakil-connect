"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, UserX } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { PageHeader } from "@/components/common/page-header";
import { FormField } from "@/components/forms/form-field";
import { SubmitButton } from "@/components/forms/submit-button";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { applyServerFieldErrors } from "@/features/auth/lib/apply-server-errors";
import { useBookAppointment } from "@/features/appointments/hooks/use-book-appointment";
import { useClientAppointments } from "@/features/appointments/hooks/use-client-appointments";
import { describeBookingError } from "@/features/appointments/lib/booking-errors";
import {
  generateSlots,
  slotsTakenByClient,
} from "@/features/appointments/lib/slots";
import {
  bookingSchema,
  type BookingFormValues,
} from "@/features/appointments/schemas/booking-schema";
import { useLawyerAvailability } from "@/features/lawyers/hooks/use-lawyer-availability";
import { useLawyerProfile } from "@/features/lawyers/hooks/use-lawyer-profile";
import { formatDateLong, formatTime } from "@/lib/date";
import { ROUTES } from "@/lib/routes";
import { isApiError, type BookAppointmentRequest } from "@/types";

import { BookingCalendar } from "./booking-calendar";
import { BookingSummary } from "./booking-summary";
import { TimeSlotPicker } from "./time-slot-picker";

/**
 * Booking flow.
 *
 * The lawyer is identified by `?lawyerId=`, which the profile CTA supplies.
 *
 * Three requests feed this screen: the lawyer's profile (name, fee, verified),
 * their availability (which dates and times exist at all), and the client's own
 * appointments (which of those times they already hold).
 */
export function BookingView() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const lawyerId = searchParams.get("lawyerId") ?? "";

  const [confirmOpen, setConfirmOpen] = useState(false);

  const profileQuery = useLawyerProfile(lawyerId);
  const { windows, isPending: availabilityLoading } =
    useLawyerAvailability(lawyerId);
  const { appointments } = useClientAppointments();

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    setError,
    reset,
    formState: { errors, isValid },
  } = useForm<BookingFormValues>({
    resolver: zodResolver(bookingSchema),
    mode: "onChange",
    defaultValues: {
      appointmentDate: "",
      appointmentTime: "",
      consultationMode: "ONLINE",
      notes: "",
    },
  });

  const selectedDate = watch("appointmentDate");
  const selectedTime = watch("appointmentTime");
  const selectedMode = watch("consultationMode");

  const slots = useMemo(
    () => (selectedDate ? generateSlots(windows, selectedDate) : []),
    [windows, selectedDate],
  );

  const takenSlots = useMemo(
    () =>
      selectedDate
        ? slotsTakenByClient(appointments, lawyerId, selectedDate)
        : new Set<string>(),
    [appointments, lawyerId, selectedDate],
  );

  // Changing the date invalidates any time already picked - the new day's
  // windows may not contain it.
  useEffect(() => {
    setValue("appointmentTime", "", { shouldValidate: true });
  }, [selectedDate, setValue]);

  const mutation = useBookAppointment({
    onSuccess: (appointment) => {
      setConfirmOpen(false);

      // Clear the selection only on success, so a failed attempt keeps both the
      // date and the time the user chose and they can retry without redoing it.
      reset();

      toast.success("Consultation requested", {
        description: `${formatDateLong(appointment.appointmentDate)} at ${formatTime(
          appointment.appointmentTime,
        )}. The lawyer will confirm shortly.`,
      });

      router.push(ROUTES.CLIENT_DASHBOARD);
    },
  });

  const submit = handleSubmit((values) => {
    const payload: BookAppointmentRequest = {
      lawyerId,
      appointmentDate: values.appointmentDate,
      appointmentTime: values.appointmentTime,
      consultationMode: values.consultationMode,
      ...(values.notes?.trim() ? { notes: values.notes.trim() } : {}),
    };

    mutation.mutate(payload, {
      onError: (error: unknown) => {
        // Close the dialog so the user can see and correct the form beneath it.
        setConfirmOpen(false);

        /*
         * A 409 is an expected business outcome here, not a fault: the slot was
         * taken between page load and submit, the lawyer's hours changed, or
         * they are not verified. Each needs a different response from the user,
         * so the message is mapped rather than passed through raw.
         *
         * The selection is deliberately left intact - the date and time stay
         * chosen so a retry is one click, not a redo.
         */
        const { title, description } = describeBookingError(error);

        // A 400 carries per-field messages; place them on the inputs, where the
        // user is looking, instead of only in a toast.
        applyServerFieldErrors(error, setError);

        toast.error(title, { description });
      },
    });
  });

  /* ------------------------------------------------------------- guards --- */

  if (!lawyerId) {
    return (
      <EmptyState
        icon={UserX}
        title="No lawyer selected"
        description="Choose a lawyer to book a consultation with."
        action={
          <Button asChild size="sm">
            <Link href={ROUTES.LAWYERS}>Find a lawyer</Link>
          </Button>
        }
      />
    );
  }

  if (profileQuery.isPending) {
    return (
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-80 w-full rounded-xl" />
        </div>
        <Skeleton className="h-72 w-full rounded-xl" />
      </div>
    );
  }

  if (profileQuery.isError) {
    const notFound = isApiError(profileQuery.error) && profileQuery.error.isNotFound;

    return notFound ? (
      <EmptyState
        icon={UserX}
        title="Lawyer not found"
        description="This profile may have been removed, or the link may be incorrect."
        action={
          <Button asChild size="sm" variant="outline">
            <Link href={ROUTES.LAWYERS}>Back to search</Link>
          </Button>
        }
      />
    ) : (
      <ErrorState
        error={profileQuery.error}
        onRetry={() => void profileQuery.refetch()}
        title="Could not load this lawyer"
      />
    );
  }

  const lawyer = profileQuery.data;

  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={ROUTES.lawyerDetail(lawyerId)}>
          <ArrowLeft aria-hidden />
          Back to profile
        </Link>
      </Button>

      <PageHeader
        title="Book a consultation"
        description={`Choose a time that works for you with ${lawyer.fullName}.`}
      />

      <form onSubmit={submit} noValidate>
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            {/* Date */}
            <Card>
              <CardHeader>
                <CardTitle>Select a date</CardTitle>
                <CardDescription>
                  Dates outside this lawyer&apos;s weekly hours are unavailable.
                </CardDescription>
              </CardHeader>

              <CardContent>
                {availabilityLoading ? (
                  <Skeleton className="h-72 w-full" />
                ) : (
                  <Controller
                    control={control}
                    name="appointmentDate"
                    render={({ field }) => (
                      <BookingCalendar
                        windows={windows}
                        value={field.value}
                        onChange={field.onChange}
                      />
                    )}
                  />
                )}

                {errors.appointmentDate ? (
                  <p className="mt-2 text-xs text-destructive" role="alert">
                    {errors.appointmentDate.message}
                  </p>
                ) : null}
              </CardContent>
            </Card>

            {/* Time */}
            {selectedDate ? (
              <Card>
                <CardHeader>
                  <CardTitle>Select a time</CardTitle>
                  <CardDescription>
                    Available start times on {formatDateLong(selectedDate)}.
                  </CardDescription>
                </CardHeader>

                <CardContent>
                  <Controller
                    control={control}
                    name="appointmentTime"
                    render={({ field }) => (
                      <TimeSlotPicker
                        slots={slots}
                        takenSlots={takenSlots}
                        value={field.value}
                        onChange={field.onChange}
                      />
                    )}
                  />

                  {errors.appointmentTime ? (
                    <p className="mt-2 text-xs text-destructive" role="alert">
                      {errors.appointmentTime.message}
                    </p>
                  ) : null}
                </CardContent>
              </Card>
            ) : null}

            {/* Details */}
            <Card>
              <CardHeader>
                <CardTitle>Consultation details</CardTitle>
              </CardHeader>

              <CardContent className="space-y-4">
                <FormField
                  label="How would you like to meet?"
                  error={errors.consultationMode?.message}
                  required
                >
                  {(field) => (
                    <Controller
                      control={control}
                      name="consultationMode"
                      render={({ field: controlled }) => (
                        <Select
                          value={controlled.value}
                          onValueChange={controlled.onChange}
                        >
                          <SelectTrigger
                            id={field.id}
                            aria-invalid={field["aria-invalid"]}
                          >
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="ONLINE">Online</SelectItem>
                            <SelectItem value="OFFLINE">In person</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                  )}
                </FormField>

                <FormField
                  label="Notes for the lawyer"
                  error={errors.notes?.message}
                  hint="Optional. Briefly describe your matter so they can prepare."
                >
                  {(field) => (
                    <Controller
                      control={control}
                      name="notes"
                      render={({ field: controlled }) => (
                        <Textarea
                          {...field}
                          value={controlled.value ?? ""}
                          onChange={controlled.onChange}
                          rows={4}
                          placeholder="For example: a contract review for a small business."
                        />
                      )}
                    />
                  )}
                </FormField>
              </CardContent>
            </Card>
          </div>

          {/* Summary */}
          <div className="space-y-4">
            <div className="space-y-4 lg:sticky lg:top-24">
              <BookingSummary
                lawyer={lawyer}
                date={selectedDate}
                time={selectedTime}
                mode={selectedMode}
              />

              <SubmitButton
                type="button"
                onClick={() => setConfirmOpen(true)}
                disabled={!isValid}
                isPending={mutation.isPending}
                pendingLabel="Requesting..."
                className="w-full"
              >
                Request consultation
              </SubmitButton>
            </div>
          </div>
        </div>
      </form>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Confirm your request"
        description={
          selectedDate && selectedTime
            ? `Request a consultation with ${lawyer.fullName} on ${formatDateLong(selectedDate)} at ${formatTime(selectedTime)}?`
            : undefined
        }
        confirmLabel="Send request"
        isPending={mutation.isPending}
        onConfirm={() => void submit()}
      />
    </div>
  );
}
