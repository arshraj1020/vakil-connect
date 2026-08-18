import type { Metadata } from "next";
import { Suspense } from "react";

import { CardSkeleton } from "@/components/common/loading-skeleton";
import { ResetPasswordForm } from "@/features/auth/components/reset-password-form";

export const metadata: Metadata = {
  title: "Reset password",
  description: "Choose a new password for your VakilConnect account.",
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
      <ResetPasswordForm />
    </Suspense>
  );
}
