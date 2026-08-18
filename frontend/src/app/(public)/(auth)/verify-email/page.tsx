import type { Metadata } from "next";
import { Suspense } from "react";

import { CardSkeleton } from "@/components/common/loading-skeleton";
import { VerifyEmailView } from "@/features/auth/components/verify-email-view";

export const metadata: Metadata = {
  title: "Verify your email",
  description: "Confirm your VakilConnect email address.",
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
      <VerifyEmailView />
    </Suspense>
  );
}
