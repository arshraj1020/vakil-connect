"use client";

import { SectionError } from "@/components/common/section-error";
import { ROUTES } from "@/lib/routes";

/** Route-level error boundary for the lawyer section. See SectionError. */
export default function LawyerSectionError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <SectionError
      error={error}
      reset={reset}
      scope="Lawyer section"
      homeHref={ROUTES.LAWYER_DASHBOARD}
      homeLabel="Go to dashboard"
    />
  );
}
