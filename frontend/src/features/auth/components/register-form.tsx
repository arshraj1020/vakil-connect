"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Briefcase, User } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";

import { FormField } from "@/components/forms/form-field";
import { FormRow, FormSection } from "@/components/forms/form-section";
import { PasswordInput } from "@/components/forms/password-input";
import { SubmitButton } from "@/components/forms/submit-button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { applyServerFieldErrors } from "@/features/auth/lib/apply-server-errors";
import { useAuth } from "@/features/auth/hooks/use-auth";
import {
  LAWYER_PROFILE_FIELDS,
  registerSchema,
  type RegisterFormValues,
} from "@/features/auth/schemas/register-schema";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { isApiError, type RegisterRequest } from "@/types";

import { SpecializationPicker } from "./specialization-picker";

const ROLE_OPTIONS = [
  {
    value: "CLIENT" as const,
    title: "I need a lawyer",
    description: "Search verified lawyers and book consultations.",
    icon: User,
  },
  {
    value: "LAWYER" as const,
    title: "I am a lawyer",
    description: "List your practice and manage appointments.",
    icon: Briefcase,
  },
];

/**
 * Sign-up form.
 *
 * Registering as a lawyer is a SINGLE atomic request: the backend creates the
 * user and the lawyer profile in one transaction, so the professional fields
 * must be collected here rather than in a follow-up step. Choosing the lawyer
 * role reveals that section; the schema requires it only in that case.
 *
 * Registration does not return a token, so there is no auto sign-in - success
 * routes to /login?registered=1.
 */
export function RegisterForm() {
  const { register: registerAccount } = useAuth();
  const router = useRouter();

  const {
    register,
    handleSubmit,
    control,
    watch,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      role: "CLIENT",
      fullName: "",
      email: "",
      password: "",
      phoneNumber: "",
      lawyerProfile: {
        barCouncilNumber: "",
        experienceYears: "",
        consultationFee: "",
        city: "",
        officeAddress: "",
        bio: "",
        specializations: [],
      },
    },
  });

  const role = watch("role");
  const isLawyer = role === "LAWYER";

  const mutation = useMutation({
    mutationFn: (values: RegisterFormValues) => {
      /*
       * Build the payload to match RegisterRequest exactly.
       *
       * phoneNumber is optional on the backend but validated by @Pattern when
       * present - an empty string would FAIL that pattern, so it must be
       * omitted rather than sent blank.
       */
      const phoneNumber = values.phoneNumber.trim();

      const payload: RegisterRequest = {
        fullName: values.fullName,
        email: values.email,
        password: values.password,
        role: values.role,
        ...(phoneNumber ? { phoneNumber } : {}),
        ...(values.role === "LAWYER"
          ? {
              lawyerProfile: {
                barCouncilNumber: values.lawyerProfile.barCouncilNumber,
                experienceYears: Number(values.lawyerProfile.experienceYears),
                consultationFee: Number(values.lawyerProfile.consultationFee),
                bio: values.lawyerProfile.bio,
                city: values.lawyerProfile.city,
                officeAddress: values.lawyerProfile.officeAddress,
                specializations: values.lawyerProfile.specializations,
              },
            }
          : {}),
      };

      return registerAccount(payload);
    },

    onSuccess: () => {
      toast.success("Account created", {
        description: "Sign in to get started.",
      });
      router.replace(`${ROUTES.LOGIN}?registered=1`);
    },

    onError: (error: unknown) => {
      /*
       * The backend reports lawyer field errors FLAT (`barCouncilNumber`) while
       * the form path is nested (`lawyerProfile.barCouncilNumber`), so the
       * known lawyer fields are re-prefixed on the way in.
       */
      const placedOnField = applyServerFieldErrors(error, setError, {
        prefix: "lawyerProfile",
        knownFields: LAWYER_PROFILE_FIELDS,
      });
      if (placedOnField) return;

      toast.error(
        isApiError(error)
          ? error.message
          : "Unable to create your account. Please try again.",
      );
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Create your account</CardTitle>
        <CardDescription>
          Join VakilConnect as a client or a legal professional.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form
          onSubmit={handleSubmit((values) => mutation.mutate(values))}
          noValidate
          className="space-y-8"
        >
          {/* Role selection ------------------------------------------------ */}
          <fieldset className="space-y-3">
            <legend className="text-sm font-medium">I am signing up as</legend>

            <div className="grid gap-3 sm:grid-cols-2">
              {ROLE_OPTIONS.map((option) => {
                const selected = role === option.value;
                const Icon = option.icon;

                return (
                  <label
                    key={option.value}
                    className={cn(
                      "flex cursor-pointer gap-3 rounded-xl border p-4 transition-colors",
                      "focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 focus-within:ring-offset-background",
                      selected
                        ? "border-primary bg-primary/5"
                        : "border-border hover:bg-accent",
                    )}
                  >
                    <input
                      type="radio"
                      value={option.value}
                      {...register("role")}
                      className="sr-only"
                    />

                    <span
                      className={cn(
                        "grid size-9 shrink-0 place-items-center rounded-lg",
                        selected
                          ? "bg-primary text-primary-foreground"
                          : "bg-muted text-muted-foreground",
                      )}
                    >
                      <Icon className="size-4" aria-hidden />
                    </span>

                    <span className="space-y-0.5">
                      <span className="block text-sm font-medium">
                        {option.title}
                      </span>
                      <span className="block text-xs text-muted-foreground">
                        {option.description}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>

          {/* Account ------------------------------------------------------- */}
          <FormSection title="Your details">
            <FormField label="Full name" error={errors.fullName?.message} required>
              {(field) => (
                <Input
                  {...field}
                  {...register("fullName")}
                  autoComplete="name"
                  autoFocus
                  placeholder="Priya Sharma"
                />
              )}
            </FormField>

            <FormRow>
              <FormField label="Email" error={errors.email?.message} required>
                {(field) => (
                  <Input
                    {...field}
                    {...register("email")}
                    type="email"
                    autoComplete="email"
                    placeholder="you@example.com"
                  />
                )}
              </FormField>

              <FormField
                label="Phone number"
                error={errors.phoneNumber?.message}
                hint="Optional"
              >
                {(field) => (
                  <Input
                    {...field}
                    {...register("phoneNumber")}
                    type="tel"
                    autoComplete="tel"
                    placeholder="9876543210"
                  />
                )}
              </FormField>
            </FormRow>

            <FormField
              label="Password"
              error={errors.password?.message}
              hint="At least 8 characters"
              required
            >
              {(field) => (
                <PasswordInput
                  {...field}
                  {...register("password")}
                  autoComplete="new-password"
                  placeholder="Create a password"
                />
              )}
            </FormField>
          </FormSection>

          {/* Lawyer profile ------------------------------------------------ */}
          {isLawyer ? (
            <FormSection
              title="Professional details"
              description="Required to list your practice. Your profile is reviewed by an administrator before it appears in search."
              className="animate-fade-in"
            >
              <FormRow>
                <FormField
                  label="Bar council number"
                  error={errors.lawyerProfile?.barCouncilNumber?.message}
                  required
                >
                  {(field) => (
                    <Input
                      {...field}
                      {...register("lawyerProfile.barCouncilNumber")}
                      placeholder="MH/1234/2020"
                    />
                  )}
                </FormField>

                <FormField
                  label="Years of experience"
                  error={errors.lawyerProfile?.experienceYears?.message}
                  required
                >
                  {(field) => (
                    <Input
                      {...field}
                      {...register("lawyerProfile.experienceYears")}
                      type="number"
                      min={0}
                      max={80}
                      placeholder="5"
                    />
                  )}
                </FormField>
              </FormRow>

              <FormRow>
                <FormField
                  label="Consultation fee"
                  error={errors.lawyerProfile?.consultationFee?.message}
                  hint="Per consultation, in INR"
                  required
                >
                  {(field) => (
                    <Input
                      {...field}
                      {...register("lawyerProfile.consultationFee")}
                      type="number"
                      min={1}
                      step="0.01"
                      placeholder="1500"
                    />
                  )}
                </FormField>

                <FormField
                  label="City"
                  error={errors.lawyerProfile?.city?.message}
                  required
                >
                  {(field) => (
                    <Input
                      {...field}
                      {...register("lawyerProfile.city")}
                      autoComplete="address-level2"
                      placeholder="Mumbai"
                    />
                  )}
                </FormField>
              </FormRow>

              <FormField
                label="Office address"
                error={errors.lawyerProfile?.officeAddress?.message}
                required
              >
                {(field) => (
                  <Input
                    {...field}
                    {...register("lawyerProfile.officeAddress")}
                    autoComplete="street-address"
                    placeholder="12 High Court Road"
                  />
                )}
              </FormField>

              {/*
                Rendered without FormField: a checkbox group needs <legend>,
                not <label>, so the picker owns its own label and error markup.
              */}
              <Controller
                control={control}
                name="lawyerProfile.specializations"
                render={({ field, fieldState }) => (
                  <SpecializationPicker
                    value={field.value}
                    onChange={field.onChange}
                    error={fieldState.error?.message}
                    hint="Select every area you practise in"
                    required
                  />
                )}
              />

              <FormField
                label="Professional bio"
                error={errors.lawyerProfile?.bio?.message}
                hint="Shown on your public profile. Up to 2000 characters."
                required
              >
                {(field) => (
                  <Textarea
                    {...field}
                    {...register("lawyerProfile.bio")}
                    rows={4}
                    placeholder="Briefly describe your practice and experience."
                  />
                )}
              </FormField>
            </FormSection>
          ) : null}

          <SubmitButton
            isPending={mutation.isPending}
            pendingLabel="Creating account..."
            className="w-full"
          >
            Create account
          </SubmitButton>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link
            href={ROUTES.LOGIN}
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
