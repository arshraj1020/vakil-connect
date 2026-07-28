import api from "@/lib/axios";
import type { LawyerProfileResponse, UpdateLawyerProfileRequest } from "@/types";

/**
 * The signed-in lawyer's own profile.
 *
 * Kept apart from `lawyer-service.ts`, whose endpoints are all anonymous
 * (`GET /api/lawyers/**` is permitAll). These two require ROLE_LAWYER and
 * derive the lawyer from the JWT, so mixing them would break that file's stated
 * invariant.
 *
 * Both endpoints resolve the lawyer by email from the token, never by id. A
 * lawyer therefore cannot read or write another's profile, and - importantly -
 * needs no knowledge of their own lawyer id, which is a different value from
 * their user id and is absent from the login response and the JWT.
 */

const ENDPOINTS = {
  profile: "/api/lawyer/profile",
} as const;

/**
 * The full profile, including fields this screen cannot edit.
 *
 * Returns 404 "Lawyer profile not found" when the account is a LAWYER with no
 * lawyer row. Registration creates both atomically, so this should not occur
 * for accounts created through the app - but a profile created out of band, or
 * a partially migrated one, would hit it, and the UI handles it explicitly
 * rather than showing a generic failure.
 */
export async function getMyProfile(): Promise<LawyerProfileResponse> {
  const { data } = await api.get<LawyerProfileResponse>(ENDPOINTS.profile);
  return data;
}

/**
 * Replaces the editable half of the profile.
 *
 * A full REPLACE, not a patch: every field on `UpdateLawyerProfileRequest` is
 * required, and the service assigns all six unconditionally. Any field omitted
 * from the payload fails validation rather than being left alone, so the form
 * must always submit the complete set - including values the lawyer did not
 * touch.
 *
 * `specializations` likewise replaces the existing set; names are resolved
 * find-or-create on the backend, so a new name silently creates a row.
 *
 * Failure modes:
 *   400 - bean validation, with per-field messages in `fieldErrors`
 *   404 - no lawyer profile for this account
 */
export async function updateMyProfile(
  payload: UpdateLawyerProfileRequest,
): Promise<LawyerProfileResponse> {
  const { data } = await api.put<LawyerProfileResponse>(
    ENDPOINTS.profile,
    payload,
  );
  return data;
}

export const lawyerProfileService = {
  getMyProfile,
  updateMyProfile,
} as const;
