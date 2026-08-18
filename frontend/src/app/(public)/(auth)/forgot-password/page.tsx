import type { Metadata } from "next";
import { Suspense } from "react";

import { CardSkeleton } from "@/components/common/loading-skeleton";
import { ForgotPasswordForm } from "@/features/auth/components/forgot-password-form";

export const metadata: Metadata = {
  title: "Forgot password",
  description: "Request a link to reset your VakilConnect password.",
};

/**
 * Server component wrapper.
 *
 * The client component reads the query string with useSearchParams, which
 * requires a Suspense boundary during static prerendering. Keeping it here lets
 * the page stay static while the interactive part resolves on the client.
 */
export default function Page() {
  return (
    <Suspense fallback={<CardSkeleton />}>
      <ForgotPasswordForm />
    </Suspense>
  );
}
