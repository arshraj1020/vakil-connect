"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { applyServerFieldErrors } from "@/features/auth/lib/apply-server-errors";
import { useUpdateProfile } from "../hooks/use-update-profile";
import { toFormValues, toUpdateRequest } from "../lib/profile-form";
import {
  LAWYER_PROFILE_FIELD_NAMES,
  lawyerProfileSchema,
  type LawyerProfileFormValues,
} from "../schemas/lawyer-profile-schema";
import { isApiError, type LawyerProfileResponse } from "@/types";

import { BiographySection } from "./biography-section";
import { ProfessionalInfoSection } from "./professional-info-section";
import { ProfilePreview } from "./profile-preview";
import { SaveChangesBar } from "./save-changes-bar";

/**
 * The editable half of the profile.
 *
 * Takes the loaded profile as a prop rather than fetching it, so the loading,
 * error and missing-profile states are resolved once by the view above and this
 * component can assume a profile exists.
 *
 * `isDirty` is the single source of truth for "are there unsaved changes". It
 * drives the save bar, the discard confirmation and the beforeunload guard, so
 * those three can never disagree. React Hook Form computes it by comparing
 * against `defaultValues`, which is why a successful save resets to the
 * SERVER's response - reverting to the pre-edit values would leave the form
 * permanently dirty, and resetting to the submitted values would hide any
 * normalisation the backend applied.
 */
export function ProfileForm({ profile }: { profile: LawyerProfileResponse }) {
  const [isDiscarding, setIsDiscarding] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    setError,
    setFocus,
    formState: { errors, isDirty },
  } = useForm<LawyerProfileFormValues>({
    resolver: zodResolver(lawyerProfileSchema),
    defaultValues: toFormValues(profile),
  });

  /*
   * Watched wholesale because the preview reflects every field. This re-renders
   * the form on each keystroke, which is acceptable here - the tree is small,
   * and the alternative (a preview that lags behind the inputs) defeats it.
   */
  const values = watch();

  const mutation = useUpdateProfile({
    onSuccess: (updated) => {
      // Reset to the server's copy: this both clears `isDirty` and adopts any
      // value the backend normalised, such as a trimmed city.
      reset(toFormValues(updated));

      toast.success("Profile saved", {
        description: profile.verified
          ? "Your changes are live on your public profile."
          : "Your changes are saved and will be visible once an administrator verifies you.",
      });
    },

    onError: (error) => {
      /*
       * A 400 carries per-field messages whose names match this form's fields
       * exactly - the form is flat, unlike registration where the same fields
       * are nested under `lawyerProfile.` and need re-prefixing.
       */
      const placedOnField = applyServerFieldErrors(error, setError);

      if (placedOnField) {
        // setError does not move focus the way a resolver failure does, so the
        // first rejected field is focused explicitly.
        const firstField = LAWYER_PROFILE_FIELD_NAMES.find(
          (name) => isApiError(error) && error.fieldErrors?.[name],
        );
        if (firstField) setFocus(firstField);
        return;
      }

      toast.error("Could not save your profile", {
        description: isApiError(error)
          ? error.status === 404
            ? "Your lawyer profile could not be found. Please sign in again."
            : error.message
          : "Please try again.",
      });
    },
  });

  /*
   * Native guard for closing the tab or following a link out of the app. Next's
   * client router cannot be intercepted from here without wrapping every Link,
   * so in-app navigation relies on the save bar being visible and sticky.
   */
  useEffect(() => {
    if (!isDirty) return;

    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty]);

  const submit = handleSubmit((formValues) => {
    if (mutation.isPending) return; // Belt and braces; SubmitButton disables too.
    mutation.mutate(toUpdateRequest(formValues));
  });

  const discard = () => {
    reset(toFormValues(profile));
    setIsDiscarding(false);
  };

  const bioLength = values.bio?.length ?? 0;

  return (
    <>
      <form onSubmit={submit} noValidate className="space-y-6">
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <ProfessionalInfoSection
              register={register}
              control={control}
              errors={errors}
            />

            <BiographySection
              register={register}
              error={errors.bio?.message}
              length={bioLength}
            />
          </div>

          <div className="space-y-6">
            <ProfilePreview profile={profile} values={values} />
          </div>
        </div>

        <SaveChangesBar
          isDirty={isDirty}
          isPending={mutation.isPending}
          onCancel={() => setIsDiscarding(true)}
        />
      </form>

      <ConfirmDialog
        open={isDiscarding}
        onOpenChange={setIsDiscarding}
        title="Discard your changes?"
        description="Your profile will go back to the last saved version. This cannot be undone."
        confirmLabel="Discard changes"
        destructive
        onConfirm={discard}
      />
    </>
  );
}
