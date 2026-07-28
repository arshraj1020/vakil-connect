import axios, { AxiosError, type AxiosInstance } from "axios";

import { ApiError, type ApiErrorResponse } from "@/types";

import { clearStoredToken, getStoredToken } from "./auth-storage";
import { SESSION_EXPIRED_PARAM, SESSION_EXPIRED_VALUE } from "./constants";
import { ROUTES } from "./routes";

/**
 * The single HTTP client for the VakilConnect API.
 *
 * Nothing in the application calls `fetch` or constructs its own Axios
 * instance: every request passes through here so that authentication, error
 * normalisation and session expiry are handled in exactly one place.
 *
 * This module intentionally knows nothing about React or Zustand - it reads the
 * token from the cookie primitive instead, which keeps the dependency graph
 * acyclic and makes the layer testable without mounting a component tree.
 */

const baseURL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const api: AxiosInstance = axios.create({
  baseURL,
  headers: { "Content-Type": "application/json" },
  /** Not "include": the JWT travels in the Authorization header, not a cookie
   *  the backend reads, and the backend's CORS policy does not allow credentials. */
  withCredentials: false,
  timeout: 20_000,
});

/* ------------------------------------------------------------------ request */

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

/* ----------------------------------------------------------------- response */

/** Endpoints where a 401 is a legitimate answer rather than an expired session. */
function isAuthEndpoint(url: string | undefined): boolean {
  return typeof url === "string" && url.includes("/api/auth/");
}

/**
 * Narrows an unknown response body to the backend's ErrorResponse shape.
 *
 * The backend returns this envelope for every handled failure, but a 502 from a
 * proxy (or an HTML error page) will not match - hence the runtime check rather
 * than a cast.
 */
function isApiErrorResponse(data: unknown): data is ApiErrorResponse {
  if (typeof data !== "object" || data === null) return false;
  const candidate = data as Partial<ApiErrorResponse>;
  return (
    typeof candidate.status === "number" && typeof candidate.message === "string"
  );
}

/** Fallback copy for responses that did not carry a usable message. */
function fallbackMessage(status: number): string {
  switch (status) {
    case 400:
      return "The request was invalid.";
    case 401:
      return "Your session has expired. Please sign in again.";
    case 403:
      return "You do not have permission to perform this action.";
    case 404:
      return "The requested resource was not found.";
    case 409:
      return "This action conflicts with the current state.";
    default:
      return status >= 500
        ? "Something went wrong on our end. Please try again."
        : "The request could not be completed.";
  }
}

function toApiError(error: unknown): ApiError {
  if (error instanceof AxiosError) {
    // No response: offline, DNS failure, timeout, or a CORS rejection.
    if (!error.response) {
      const message =
        error.code === "ECONNABORTED"
          ? "The request timed out. Please try again."
          : "Unable to reach the server. Check your connection and try again.";
      return new ApiError(0, message);
    }

    const { status, data } = error.response;

    if (isApiErrorResponse(data)) {
      return new ApiError(
        status,
        data.message || fallbackMessage(status),
        data.fieldErrors ?? null,
        data.path ?? null,
      );
    }

    return new ApiError(status, fallbackMessage(status));
  }

  return new ApiError(0, "An unexpected error occurred.");
}

/**
 * Ends the session and sends the user to the login screen.
 *
 * A hard navigation (rather than the Next router) is deliberate: it discards
 * all in-memory state, including the Zustand auth store and the TanStack Query
 * cache, so no fragment of the previous session can survive.
 */
function handleExpiredSession(): void {
  if (typeof window === "undefined") return;

  // Already on the login screen - clearing is enough, redirecting would loop.
  if (window.location.pathname === ROUTES.LOGIN) {
    clearStoredToken();
    return;
  }

  clearStoredToken();
  window.location.assign(
    `${ROUTES.LOGIN}?${SESSION_EXPIRED_PARAM}=${SESSION_EXPIRED_VALUE}`,
  );
}

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error);

    /*
     * 401 means the token is missing, invalid or expired.
     *
     * Excluded: /api/auth/** - a wrong password legitimately returns 401 and
     * must surface as a form error, not bounce the user off the login page.
     */
    const requestUrl =
      error instanceof AxiosError ? error.config?.url : undefined;

    if (apiError.status === 401 && !isAuthEndpoint(requestUrl)) {
      handleExpiredSession();
    }

    return Promise.reject(apiError);
  },
);

export default api;
