import api from "@/lib/axios";
import type { CurrentUserResponse, UpdateClientProfileRequest } from "@/types";

/**
 * Account endpoints for the authenticated user.
 *
 * Identity always comes from the JWT - no endpoint here accepts a user id, so
 * one account can never read or modify another.
 */

const ENDPOINTS = {
  me: "/api/users/me",
  clientProfile: "/api/client/profile",
} as const;

/**
 * The authoritative session check.
 *
 * A cookie only proves a token exists; it cannot prove the token is valid,
 * unexpired, or that the account is still active. This endpoint can, which is
 * why it is the source of truth during hydration.
 *
 * Role-agnostic: valid for CLIENT, LAWYER and ADMIN alike.
 */
export async function getCurrentUser(): Promise<CurrentUserResponse> {
  const { data } = await api.get<CurrentUserResponse>(ENDPOINTS.me);
  return data;
}

/**
 * Updates the signed-in client's own profile.
 *
 * Only `fullName` and `phoneNumber` are editable; email, password, role and
 * active status are absent from the request DTO and cannot be changed here.
 *
 * CLIENT-scoped: a LAWYER or ADMIN token receives 403.
 */
export async function updateClientProfile(
  payload: UpdateClientProfileRequest,
): Promise<CurrentUserResponse> {
  const { data } = await api.put<CurrentUserResponse>(
    ENDPOINTS.clientProfile,
    payload,
  );
  return data;
}

export const userService = { getCurrentUser, updateClientProfile } as const;
