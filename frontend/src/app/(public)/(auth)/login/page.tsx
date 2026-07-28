import type { Metadata } from "next";
import { Suspense } from "react";

import { CardSkeleton } from "@/components/common/loading-skeleton";
import { LoginForm } from "@/features/auth/components/login-form";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Sign in to your VakilConnect account.",
};

/**
 * Server component wrapper.
 *
 * LoginForm reads the query string (?session=expired, ?registered=1, ?next=)
 * with useSearchParams, which requires a Suspense boundary during static
 * prerendering. Keeping the boundary here lets the page stay static while the
 * form resolves on the client.
 */
export default function LoginPage() {
  return (
    <Suspense fallback={<CardSkeleton />}>
      <LoginForm />
    </Suspense>
  );
}
