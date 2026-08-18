"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { FormField } from "@/components/forms/form-field";
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
import { applyServerFieldErrors } from "@/features/auth/lib/apply-server-errors";
import { isEmailNotVerified } from "@/features/auth/lib/identity-errors";
import {
  loginSchema,
  type LoginFormValues,
} from "@/features/auth/schemas/login-schema";
import { useAuth } from "@/features/auth/hooks/use-auth";
import {
  REDIRECT_PARAM,
  SESSION_EXPIRED_PARAM,
  SESSION_EXPIRED_VALUE,
} from "@/lib/constants";
import { ROUTES, safeRedirect } from "@/lib/routes";

import { ResendVerificationForm } from "./resend-verification-form";
import { isApiError } from "@/types";

/**
 * Sign-in form.
 *
 * The request sequence lives in `useAuth.login()`:
 *   POST /api/auth/login -> store token -> GET /api/users/me -> hydrate store
 *
 * Wrapping it in `useMutation` is for the UI state only - `isPending` drives the
 * disabled/spinner treatment - not for caching. Retries are already off for
 * mutations, which matters here: re-sending credentials automatically would be
 * wrong.
 */
export function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const sessionExpired =
    searchParams.get(SESSION_EXPIRED_PARAM) === SESSION_EXPIRED_VALUE;
  const justRegistered = searchParams.get("registered") === "1";

  /*
   * Set when the backend answers 403 EMAIL_NOT_VERIFIED. Holds the address the
   * user just typed so the resend panel can prefill it - they should not have
   * to type it again to fix a problem they did not cause.
   */
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    getValues,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const mutation = useMutation({
    mutationFn: (values: LoginFormValues) =>
      login({ email: values.email, password: values.password }),

    onSuccess: (user) => {
      toast.success(`Welcome back, ${user.fullName.split(" ")[0] ?? ""}`.trim());

      /*
       * `next` is validated against the role that just signed in, never trusted
       * as given. It is attacker-controllable, and it is routinely STALE: it is
       * captured when a session ends, so signing out of a lawyer account and
       * back in as a client used to land on /lawyer/dashboard and render
       * "Access denied". safeRedirect falls back to this user's own dashboard.
       */
      router.replace(safeRedirect(searchParams.get(REDIRECT_PARAM), user.role));
    },

    onError: (error: unknown) => {
      // 401 from /api/auth/** is a genuine credential failure, not an expired
      // session - the interceptor leaves it to us precisely so it can be shown
      // here rather than triggering a redirect.
      const placedOnField = applyServerFieldErrors(error, setError);
      if (placedOnField) return;

      /*
       * EMAIL_NOT_VERIFIED is a 403, not a 401: the password was CORRECT.
       * Showing "incorrect email or password" here would send the user off to
       * reset a password that works perfectly well. Surfacing the resend panel
       * instead puts the only useful action in front of them.
       */
      if (isEmailNotVerified(error)) {
        setUnverifiedEmail(getValues("email"));
        return;
      }

      toast.error(
        isApiError(error) && error.status === 401
          ? "Incorrect email or password."
          : isApiError(error)
            ? error.message
            : "Unable to sign in. Please try again.",
      );
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Sign in</CardTitle>
        <CardDescription>
          Access your consultations and appointments.
        </CardDescription>
      </CardHeader>

      <CardContent>
        {unverifiedEmail ? (
          <div className="space-y-4">
            <div className="space-y-1">
              <p className="text-sm font-medium">Verify your email to continue</p>
              <p className="text-sm text-muted-foreground">
                Your password was correct, but this address has not been
                confirmed yet. Open the link we emailed you, or send a new one.
              </p>
            </div>
            <ResendVerificationForm defaultEmail={unverifiedEmail} />
            <button
              type="button"
              onClick={() => setUnverifiedEmail(null)}
              className="text-sm font-medium text-primary underline-offset-4 hover:underline"
            >
              Back to sign in
            </button>
          </div>
        ) : (
          <>
            {sessionExpired ? (
          <p
            role="status"
            className="mb-5 rounded-lg bg-warning/10 px-3 py-2 text-sm text-warning"
          >
            Your session expired. Please sign in again.
          </p>
        ) : null}

        {justRegistered ? (
          <p
            role="status"
            className="mb-5 rounded-lg bg-success/10 px-3 py-2 text-sm text-success"
          >
            Your account is ready. Sign in to continue.
          </p>
        ) : null}

        <form
          onSubmit={handleSubmit((values) => mutation.mutate(values))}
          noValidate
          className="space-y-4"
        >
          <FormField label="Email" error={errors.email?.message} required>
            {(field) => (
              <Input
                {...field}
                {...register("email")}
                type="email"
                autoComplete="email"
                autoFocus
                placeholder="you@example.com"
              />
            )}
          </FormField>

          <FormField label="Password" error={errors.password?.message} required>
            {(field) => (
              <PasswordInput
                {...field}
                {...register("password")}
                autoComplete="current-password"
                placeholder="Enter your password"
              />
            )}
          </FormField>

          {/*
            "Remember me" was removed rather than wired up. The backend issues a
            fixed 24h token and has no refresh endpoint, so the cookie's lifetime
            cannot extend a session beyond the token's own expiry - the checkbox
            could not have done anything except mislead. Restoring it needs a
            backend change (longer expiry, or refresh tokens), not a UI change.
          */}
          <div className="flex items-center justify-end gap-3">
            {/* Phase 7: the backend reset flow exists, so this is a real link. */}
            <Link
              href={ROUTES.FORGOT_PASSWORD}
              className="text-sm font-medium text-primary underline-offset-4 hover:underline"
            >
              Forgot password?
            </Link>
          </div>

          <SubmitButton
            isPending={mutation.isPending}
            pendingLabel="Signing in..."
            className="w-full"
          >
            Sign in
          </SubmitButton>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          New to VakilConnect?{" "}
          <Link
            href={ROUTES.REGISTER}
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            Create an account
          </Link>
        </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}
