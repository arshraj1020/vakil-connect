import api from "@/lib/axios";
import type { CurrentUserResponse, UpdateClientProfileRequest } from "@/types";

/**
 * The signed-in client's own account.
 *
 * Both endpoints require ROLE_CLIENT and resolve the user from the JWT, so a
 * client can never read or write another account.
 *
 * `GET /api/client/profile` returns the same `CurrentUserResponse` as
 * `GET /api/users/me` for the same person - the client controller exposes it
 * under this path so read and write share one URL. The frontend therefore keeps
 * ONE cache entry for the caller's account (`queryKeys.auth.currentUser()`)
 * rather than two that could disagree.
 */

const ENDPOINTS = {
  profile: "/api/client/profile",
} as const;

/** The caller's account record. */
export async function getMyProfile(): Promise<CurrentUserResponse> {
  const { data } = await api.get<CurrentUserResponse>(ENDPOINTS.profile);
  return data;
}

/**
 * Updates the two editable fields.
 *
 * `UpdateClientProfileRequest` carries only `fullName` and `phoneNumber`;
 * `email`, `password`, `role` and `active` are absent from the DTO by design and
 * cannot be changed through this route.
 *
 * `phoneNumber` must be OMITTED rather than sent empty when the user clears it.
 * Bean Validation's `@Pattern` passes `null` but rejects `""`, so an empty
 * string would fail with a 400 where omitting the key succeeds.
 *
 * Failure modes:
 *   400 - bean validation, with per-field messages in `fieldErrors`
 *   404 - the account no longer exists
 */
export async function updateMyProfile(
  payload: UpdateClientProfileRequest,
): Promise<CurrentUserResponse> {
  const { data } = await api.put<CurrentUserResponse>(ENDPOINTS.profile, payload);
  return data;
}

export const clientProfileService = {
  getMyProfile,
  updateMyProfile,
} as const;
