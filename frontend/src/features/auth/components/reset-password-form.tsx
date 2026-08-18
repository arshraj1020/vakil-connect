"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

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
import {
  classifyTokenError,
  type TokenOutcomeCopy,
} from "@/features/auth/lib/identity-errors";
import {
  resetPasswordSchema,
  type ResetPasswordValues,
} from "@/features/auth/schemas/password-reset-schema";
import { ROUTES } from "@/lib/routes";
import { identityService } from "@/services/identity-service";

/**
 * Sets a new password from an emailed link.
 *
 * THE TOKEN IS READ ONCE AND HELD IN A REF, then stripped from the address bar.
 * Keeping it out of the URL keeps it out of browser history and out of any
 * `Referer` header. It is submitted in the POST body - never as a query
 * parameter, and neither is the password.
 *
 * Unlike the verification page this does NOT auto-submit: consuming a
 * single-use token requires the new password, so the token is only spent when
 * the user actually submits the form. A prefetcher hitting this URL therefore
 * costs nothing.
 */
export function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const token = useRef<string | null>(null);
  const [ready, setReady] = useState(false);
  const [failure, setFailure] = useState<TokenOutcomeCopy | null>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    token.current = searchParams.get("token");
    if (token.current) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    setReady(true);
  }, [searchParams]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  async function onSubmit(values: ResetPasswordValues) {
    if (!token.current) return;

    try {
      // confirmPassword is never sent - the backend has no business knowing
      // the user typed it twice.
      await identityService.resetPassword(token.current, values.newPassword);
      setDone(true);
      toast.success("Password updated. Please sign in.");
      router.replace(ROUTES.LOGIN);
    } catch (error) {
      setFailure(classifyTokenError(error));
    }
  }

  if (!ready) return null;

  if (!token.current && !done) {
    return (
      <Shell
        title="No reset link found"
        description="Open the link from your email, or request a new one."
      >
        <Button asChild className="w-full">
          <Link href={ROUTES.FORGOT_PASSWORD}>Request a new link</Link>
        </Button>
      </Shell>
    );
  }

  if (failure) {
    return (
      <Shell title={failure.title} description={failure.description}>
        {failure.offerNewLink ? (
          <Button asChild className="w-full">
            <Link href={ROUTES.FORGOT_PASSWORD}>Request a new link</Link>
          </Button>
        ) : null}
        <Button
          asChild
          variant={failure.offerNewLink ? "ghost" : "default"}
          className="w-full"
        >
          <Link href={ROUTES.LOGIN}>Go to sign in</Link>
        </Button>
      </Shell>
    );
  }

  if (done) {
    return (
      <Shell
        title="Password updated"
        description="You have been signed out everywhere. Sign in with your new password."
      >
        <Button asChild className="w-full">
          <Link href={ROUTES.LOGIN}>Continue to sign in</Link>
        </Button>
      </Shell>
    );
  }

  return (
    <Shell
      title="Choose a new password"
      description="You will be signed out on every device once this is saved."
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="newPassword">New password</Label>
          <Input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            autoFocus
            aria-invalid={errors.newPassword ? true : undefined}
            aria-describedby={
              errors.newPassword ? "newPassword-error" : undefined
            }
            {...register("newPassword")}
          />
          {errors.newPassword ? (
            <p
              id="newPassword-error"
              role="alert"
              className="text-sm text-destructive"
            >
              {errors.newPassword.message}
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="confirmPassword">Confirm new password</Label>
          <Input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            aria-invalid={errors.confirmPassword ? true : undefined}
            aria-describedby={
              errors.confirmPassword ? "confirmPassword-error" : undefined
            }
            {...register("confirmPassword")}
          />
          {errors.confirmPassword ? (
            <p
              id="confirmPassword-error"
              role="alert"
              className="text-sm text-destructive"
            >
              {errors.confirmPassword.message}
            </p>
          ) : null}
        </div>

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : "Set new password"}
        </Button>
      </form>
    </Shell>
  );
}

function Shell({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">{children}</CardContent>
    </Card>
  );
}
