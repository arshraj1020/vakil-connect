import type { Uuid } from "./common";
import type { CreateLawyerProfileRequest } from "./lawyer";

/**
 * Authentication, session and account-profile contracts.
 */

/** Roles as stored on `User.role` and emitted as `ROLE_<name>` authorities. */
export type Role = "CLIENT" | "LAWYER" | "ADMIN";

/**
 * Roles obtainable through public registration.
 *
 * ADMIN is deliberately excluded: the backend validates `role` against
 * `CLIENT|LAWYER` and rejects anything else with 400. Admin accounts are
 * created by the server-side bootstrap runner, never through the API.
 */
export type RegisterableRole = Extract<Role, "CLIENT" | "LAWYER">;

/**
 * `POST /api/auth/register`.
 *
 * Lawyer signup is SINGLE-STEP and ATOMIC: when `role` is `"LAWYER"`,
 * `lawyerProfile` is required and the backend creates the `User` and `Lawyer`
 * rows in one transaction. Omitting it yields 400 listing the missing fields;
 * a duplicate bar council number rolls the whole registration back with 409.
 *
 * For `"CLIENT"`, `lawyerProfile` must be omitted.
 */
export interface RegisterRequest {
  /** Max 150 characters. */
  fullName: string;
  email: string;
  /** Minimum 8 characters. */
  password: string;
  /** Optional. Pattern: `^\+?[0-9]{10,15}$`. */
  phoneNumber?: string;
  /** Defaults to CLIENT on the backend when omitted. */
  role?: RegisterableRole;
  /** Required when `role` is `"LAWYER"`, otherwise omitted. */
  lawyerProfile?: CreateLawyerProfileRequest;
}

export interface RegisterResponse {
  id: Uuid;
  fullName: string;
  email: string;
  role: Role;
  message: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * `POST /api/auth/login`.
 *
 * There is no refresh token. The JWT is valid for 24h; on expiry the API
 * returns 401 and the user must log in again.
 */
export interface LoginResponse {
  /** Raw JWT, to be sent as `Authorization: Bearer <token>`. */
  token: string;
  /** Always `"Bearer"`. */
  tokenType: string;
  fullName: string;
  email: string;
  role: Role;
}

/**
 * `GET /api/users/me` - the authoritative session check.
 *
 * A cookie can be stale or tampered with; this endpoint cannot. Session
 * bootstrap on page load calls it to confirm the token is still valid.
 */
export interface CurrentUserResponse {
  id: Uuid;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
}

/**
 * `PUT /api/client/profile`.
 *
 * Only these two fields are editable. Email, password, role and active status
 * are absent from the DTO by design and cannot be changed through this route.
 */
export interface UpdateClientProfileRequest {
  /** Max 150 characters. */
  fullName: string;
  /** Pattern: `^\+?[0-9]{10,15}$`. */
  phoneNumber?: string;
}
