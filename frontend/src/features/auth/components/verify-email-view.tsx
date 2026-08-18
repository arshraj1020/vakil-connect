"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import {
  classifyTokenError,
  type TokenOutcomeCopy,
} from "@/features/auth/lib/identity-errors";
import { ROUTES } from "@/lib/routes";
import { identityService } from "@/services/identity-service";

import { ResendVerificationForm } from "./resend-verification-form";

type State =
  | { kind: "verifying" }
  | { kind: "success" }
  | { kind: "missing-token" }
  | { kind: "failed"; copy: TokenOutcomeCopy };

/**
 * Consumes the token from `?token=` and POSTs it.
 *
 * WHY A GET LANDS HERE AND A POST GOES TO THE API. The emailed link must be a
 * plain navigation - mail scanners, corporate link-checkers and browser
 * prefetchers all fetch links before a human clicks. If that fetch consumed the
 * token the user would arrive to "invalid link" every time. This page renders
 * on GET and only calls the mutating endpoint from an effect.
 *
 * The token is stripped from the address bar immediately after being read, so
 * it does not linger in browser history or leak through a `Referer` header if
 * the user later clicks an outbound link.
 */
export function VerifyEmailView() {
  const searchParams = useSearchParams();
  const [state, setState] = useState<State>({ kind: "verifying" });

  /*
   * React 18 StrictMode double-invokes effects in development. Without this
   * guard the token would be POSTed twice, the second call would legitimately
   * return TOKEN_ALREADY_USED, and a successful verification would render as a
   * failure - only in dev, which is the most confusing kind of bug.
   */
  const attempted = useRef(false);

  useEffect(() => {
    if (attempted.current) return;
    attempted.current = true;

    const token = searchParams.get("token");

    if (!token) {
      setState({ kind: "missing-token" });
      return;
    }

    // Remove the token from the URL before the request resolves.
    window.history.replaceState(null, "", window.location.pathname);

    identityService
      .verifyEmail(token)
      .then(() => setState({ kind: "success" }))
      .catch((error) =>
        setState({ kind: "failed", copy: classifyTokenError(error) }),
      );
  }, [searchParams]);

  if (state.kind === "verifying") {
    return (
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Verifying your email</CardTitle>
          <CardDescription>This will only take a moment.</CardDescription>
        </CardHeader>
        <CardContent className="flex justify-center py-8">
          <Spinner aria-label="Verifying your email address" />
        </CardContent>
      </Card>
    );
  }

  if (state.kind === "success") {
    return (
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Email verified</CardTitle>
          <CardDescription>
            Your address is confirmed. You can sign in now.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild className="w-full">
            <Link href={ROUTES.LOGIN}>Continue to sign in</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (state.kind === "missing-token") {
    return (
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>No verification link found</CardTitle>
          <CardDescription>
            Open the link from your email, or request a new one below.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <ResendVerificationForm />
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
        <CardTitle>{state.copy.title}</CardTitle>
        <CardDescription>{state.copy.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/*
          An already-used link usually means it worked the first time, so the
          useful action is signing in - not sending another email nobody needs.
        */}
        {state.copy.offerNewLink ? <ResendVerificationForm /> : null}
        <Button
          asChild
          variant={state.copy.offerNewLink ? "ghost" : "default"}
          className="w-full"
        >
          <Link href={ROUTES.LOGIN}>Go to sign in</Link>
        </Button>
      </CardContent>
    </Card>
  );
}
