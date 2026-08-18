"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { isCooldownActive } from "@/features/auth/lib/identity-errors";
import {
  forgotPasswordSchema,
  type ForgotPasswordValues,
} from "@/features/auth/schemas/password-reset-schema";
import { ROUTES } from "@/lib/routes";
import { identityService } from "@/services/identity-service";
import { isApiError } from "@/types";

/**
 * Requests a password-reset link.
 *
 * THE SUCCESS SCREEN IS SHOWN FOR EVERY OUTCOME THE BACKEND TREATS AS SUCCESS,
 * including an address that has no account. That is not sloppiness - the API
 * answers an identical 202 precisely so this page cannot become an
 * account-enumeration oracle, and rendering "no account found" here would throw
 * that away. The wording is therefore conditional and confirms nothing.
 *
 * A 429 cooldown is the one case that necessarily differs, and it still reveals
 * only what the person in front of the screen just did.
 */
export function ForgotPasswordForm() {
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  async function onSubmit(values: ForgotPasswordValues) {
    setFormError(null);
    try {
      await identityService.forgotPassword(values.email);
      setSubmitted(true);
    } catch (error) {
      if (isCooldownActive(error)) {
        // Already sent one recently. Showing the same confirmation avoids
        // implying anything about whether the account exists.
        setSubmitted(true);
        return;
      }
      setFormError(
        isApiError(error)
          ? error.message
          : "Unable to send that right now. Please try again.",
      );
    }
  }

  if (submitted) {
    return (
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Check your email</CardTitle>
          <CardDescription>
            If an account exists for that address, we have sent a link to reset
            your password. It expires in 30 minutes.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Nothing arrived? Check your spam folder before requesting another.
          </p>
          <Button asChild variant="ghost" className="w-full">
            <Link href={ROUTES.LOGIN}>Back to sign in</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>Forgot your password?</CardTitle>
        <CardDescription>
          Enter your email address and we will send you a link to set a new one.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="email">Email address</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              autoFocus
              placeholder="you@example.com"
              aria-invalid={errors.email ? true : undefined}
              aria-describedby={errors.email ? "email-error" : undefined}
              {...register("email")}
            />
            {errors.email ? (
              <p id="email-error" role="alert" className="text-sm text-destructive">
                {errors.email.message}
              </p>
            ) : null}
          </div>

          {formError ? (
            <p role="alert" className="text-sm text-destructive">
              {formError}
            </p>
          ) : null}

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Sending…" : "Send reset link"}
          </Button>

          <Button asChild variant="ghost" className="w-full">
            <Link href={ROUTES.LOGIN}>Back to sign in</Link>
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
