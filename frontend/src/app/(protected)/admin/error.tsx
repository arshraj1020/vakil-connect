"use client";

import { SectionError } from "@/components/common/section-error";
import { ROUTES } from "@/lib/routes";

/** Route-level error boundary for the admin section. See SectionError. */
export default function AdminSectionError({
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
      scope="Admin section"
      homeHref={ROUTES.ADMIN_DASHBOARD}
      homeLabel="Go to dashboard"
    />
  );
}
