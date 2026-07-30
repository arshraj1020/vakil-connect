"use client";

import { SectionError } from "@/components/common/section-error";
import { ROUTES } from "@/lib/routes";

/** Route-level error boundary for the public section. See SectionError. */
export default function PublicSectionError({
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
      scope="Public section"
      homeHref={ROUTES.HOME}
      homeLabel="Back to home"
    />
  );
}
