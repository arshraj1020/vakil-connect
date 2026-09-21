import api from "@/lib/axios";
import { getLawyerProfile } from "@/services/lawyer-service";
import type {
  LawyerProfileResponse,
  LawyerSummaryResponse,
  Paged,
  PageParams,
} from "@/types";

/**
 * Lawyer verification.
 *
 * What the API does NOT provide, and therefore what this module cannot offer:
 *   - no un-verify endpoint (verifying is still irreversible on its own)
 *   - no search or filter parameters on the pending queue
 *   - no bulk verification or bulk rejection
 *   - no ordering, and no `createdAt` to order by
 *
 * Rejection IS supported (`/reject`) but is deliberately not the inverse of
 * verification: it does not delete the application, and it is not terminal -
 * the lawyer's next profile edit clears it server-side and puts them back in
 * the pending queue. See `LawyerProfileResponse.rejected`.
 */

const ENDPOINTS = {
  pending: "/api/admin/lawyers/pending",
  verify: (lawyerId: string) => `/api/admin/lawyers/${lawyerId}/verify`,
  reject: (lawyerId: string) => `/api/admin/lawyers/${lawyerId}/reject`,
} as const;

/**
 * One page of lawyers awaiting verification.
 *
 * Backed by `findByVerifiedFalse(pageable)`, which emits no ORDER BY; the
 * controller builds `PageRequest.of(page, size)` with no Sort. The queue order
 * is therefore whatever Postgres returns, and it shifts as rows are updated -
 * so a page is a slice, not a ranking, and callers must not label it "oldest"
 * or "newest".
 *
 * `LawyerSummaryResponse` has no `createdAt`, so the queue cannot be ordered
 * client-side either.
 */
export async function getPendingLawyers(
  params: PageParams,
): Promise<Paged<LawyerSummaryResponse>> {
  const { data } = await api.get<Paged<LawyerSummaryResponse>>(
    ENDPOINTS.pending,
    { params },
  );
  return data;
}

/**
 * The full profile of a lawyer awaiting verification.
 *
 * Delegates to the PUBLIC detail endpoint rather than duplicating the request.
 * That is legitimate and deliberate: `GET /api/lawyers/{id}` is permitAll and
 * resolves with a plain `findById` carrying NO verified predicate, so it
 * returns unverified lawyers too - unlike `GET /api/lawyers` search, which
 * hardcodes `verified = true`.
 *
 * This is the only way to see the fields verification actually depends on:
 * `barCouncilNumber`, `email`, `phoneNumber`, `bio` and `officeAddress` are all
 * absent from `LawyerSummaryResponse`.
 */
export function getLawyerForReview(
  lawyerId: string,
): Promise<LawyerProfileResponse> {
  return getLawyerProfile(lawyerId);
}

/**
 * Marks a lawyer verified.
 *
 * IRREVERSIBLE through the API. The service sets `verified = true` with no
 * inverse operation anywhere in the controller, so this cannot be undone by any
 * admin action - the UI must confirm before calling it.
 *
 * Not guarded by current state: the service does not check whether the lawyer
 * is already verified, it simply assigns true again. A duplicate submission is
 * therefore harmless and returns 200 rather than a conflict.
 *
 * Returns the updated `LawyerProfileResponse` - a richer shape than the
 * `LawyerSummaryResponse` that appears in the queue.
 *
 * Returns 404 for an unknown lawyerId.
 */
export async function verifyLawyer(
  lawyerId: string,
): Promise<LawyerProfileResponse> {
  const { data } = await api.put<LawyerProfileResponse>(
    ENDPOINTS.verify(lawyerId),
  );
  return data;
}

/**
 * Declines a pending application. `reason` is optional and shown back to the
 * lawyer; omit it (or pass an empty string) to decline without one.
 *
 * Not guarded by current state, same as `verifyLawyer`: rejecting an
 * already-rejected or already-verified lawyer succeeds and just overwrites
 * the flags, rather than conflicting.
 */
export async function rejectLawyer(
  lawyerId: string,
  reason?: string,
): Promise<LawyerProfileResponse> {
  const { data } = await api.put<LawyerProfileResponse>(
    ENDPOINTS.reject(lawyerId),
    { reason: reason?.trim() || undefined },
  );
  return data;
}

export const adminLawyerService = {
  getPendingLawyers,
  getLawyerForReview,
  verifyLawyer,
  rejectLawyer,
} as const;
