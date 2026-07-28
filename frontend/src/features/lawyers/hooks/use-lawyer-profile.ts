"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { lawyerService } from "@/services/lawyer-service";

/**
 * A single lawyer's public profile.
 *
 * A 404 (unknown id) is not retried - the shared query client already skips
 * retries for 4xx, since re-requesting a resource the server says does not
 * exist only delays the error.
 */
export function useLawyerProfile(lawyerId: string) {
  return useQuery({
    queryKey: queryKeys.lawyers.detail(lawyerId),
    queryFn: () => lawyerService.getLawyerProfile(lawyerId),
    enabled: Boolean(lawyerId),
  });
}
