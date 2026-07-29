"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

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
import { Input } from "@/components/ui/input";
import { applyServerFieldErrors } from "@/features/auth/lib/apply-server-errors";
import { isApiError, type CurrentUserResponse, type UpdateClientProfileRequest } from "@/types";

import { useUpdateClientProfile } from "../hooks/use-update-client-profile";
import {
  CLIENT_PROFILE_FIELD_NAMES,
  clientProfileSchema,
  type ClientProfileFormValues,
} from "../schemas/client-profile-schema";

/**
 * The editable half of a client's account.
 *
 * Two fields, so no section components and no field-mapping module - both would
 * be indirection around a single `reset()` call. The lawyer profile splits into
 * sections because it edits six fields across two concerns; this does not.
 *
 * `isDirty` gates the save controls, and a successful save resets to the
 * SERVER's response rather than the submitted values, so any normalisation the
 * backend applied (a trimmed name) is adopted rather than hidden.
 */
export function ClientProfileForm({ profile }: { profile: CurrentUserResponse }) {
  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    formState: { errors, isDirty },
  } = useForm<ClientProfileFormValues>({
    resolver: zodResolver(clientProfileSchema),
    defaultValues: {
      fullName: profile.fullName,
      // The API returns null for an absent number; an input needs a string.
      phoneNumber: profile.phoneNumber ?? "",
    },
  });

  const mutation = useUpdateClientProfile({
    onSuccess: (updated) => {
      reset({
        fullName: updated.fullName,
        phoneNumber: updated.phoneNumber ?? "",
      });

      toast.success("Profile saved", {
        description: "Your details have been updated.",
      });
    },

    onError: (error) => {
      // Field names match this form's exactly - it is flat, unlike registration
      // where the same two sit beside a nested `lawyerProfile.`.
      const placedOnField = applyServerFieldErrors(error, setError);

      if (placedOnField) {
        // setError does not move focus the way a resolver failure does.
        const firstField = CLIENT_PROFILE_FIELD_NAMES.find(
          (name) => isApiError(error) && error.fieldErrors?.[name],
        );
        if (firstField) setFocus(firstField);
        return;
      }

      toast.error("Could not save your profile", {
        description: isApiError(error)
          ? error.status === 404
            ? "Your account could not be found. Please sign in again."
            : error.message
          : "Please try again.",
      });
    },
  });

  const submit = handleSubmit((values) => {
    if (mutation.isPending) return; // SubmitButton disables too; belt and braces.

    const phoneNumber = values.phoneNumber.trim();

    /*
     * `phoneNumber` is omitted rather than sent empty when cleared: the
     * backend's @Pattern passes null but rejects "", so an empty string would
     * be a 400. Same treatment as registration.
     */
    const payload: UpdateClientProfileRequest = {
      fullName: values.fullName,
      ...(phoneNumber ? { phoneNumber } : {}),
    };

    mutation.mutate(payload);
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your details</CardTitle>
        <CardDescription>
          Lawyers see your name on the consultations you book.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form onSubmit={submit} noValidate className="space-y-4">
          <FormField label="Full name" error={errors.fullName?.message} required>
            {(field) => (
              <Input
                {...field}
                {...register("fullName")}
                autoComplete="name"
                placeholder="Your full name"
              />
            )}
          </FormField>

          <FormField
            label="Phone number"
            error={errors.phoneNumber?.message}
            hint="Optional. Used by lawyers to reach you about a consultation."
          >
            {(field) => (
              <Input
                {...field}
                {...register("phoneNumber")}
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                placeholder="9876543210"
              />
            )}
          </FormField>

          <div className="flex items-center justify-end gap-2 pt-2">
            {isDirty ? (
              <Button
                type="button"
                variant="outline"
                onClick={() =>
                  reset({
                    fullName: profile.fullName,
                    phoneNumber: profile.phoneNumber ?? "",
                  })
                }
                disabled={mutation.isPending}
              >
                Cancel
              </Button>
            ) : null}

            <SubmitButton
              isPending={mutation.isPending}
              pendingLabel="Saving..."
              disabled={!isDirty}
            >
              Save changes
            </SubmitButton>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
