import api from "@/lib/axios";
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from "@/types";

/**
 * Authentication endpoints.
 *
 * Services are pure HTTP: they issue a request, return a typed DTO, and let
 * errors propagate as the `ApiError` produced by the Axios interceptor. They
 * hold no state and know nothing about React, so they can be exercised in
 * isolation and reused from anywhere.
 */

const ENDPOINTS = {
  login: "/api/auth/login",
  register: "/api/auth/register",
} as const;

/**
 * Exchanges credentials for a JWT.
 *
 * Note the response carries no `id` or `phoneNumber`; callers that need the
 * full account record should follow up with `userService.getCurrentUser()`.
 *
 * A wrong password returns 401. The Axios interceptor deliberately does NOT
 * treat that as an expired session for `/api/auth/**`, so it surfaces here as
 * an ordinary `ApiError` the login form can render.
 */
export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>(ENDPOINTS.login, payload);
  return data;
}

/**
 * Creates a CLIENT or LAWYER account.
 *
 * Registration does NOT return a token and does not establish a session - the
 * caller should send the user to the login screen afterwards.
 *
 * For `role: "LAWYER"` the nested `lawyerProfile` is mandatory; the backend
 * creates the user and lawyer records in a single transaction, so a rejected
 * profile (e.g. a duplicate bar council number, 409) leaves no account behind.
 */
export async function register(
  payload: RegisterRequest,
): Promise<RegisterResponse> {
  const { data } = await api.post<RegisterResponse>(ENDPOINTS.register, payload);
  return data;
}

export const authService = { login, register } as const;
