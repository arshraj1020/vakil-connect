import api from "@/lib/axios";
import type { AvailabilityResponse, CreateAvailabilityRequest } from "@/types";

/**
 * The signed-in lawyer's own availability windows.
 *
 * Distinct from the public endpoint used on a lawyer's profile
 * (`/api/lawyers/{id}/availability`): these routes take no id, deriving the
 * lawyer from the JWT, so one lawyer can never read or alter another's hours.
 *
 * NOTE the API surface: create and delete only. There is no update endpoint, so
 * editing a window is composed from the two - see `useUpdateAvailability`.
 */

const ENDPOINTS = {
  availability: "/api/lawyer/availability",
  window: (availabilityId: string) => `/api/lawyer/availability/${availabilityId}`,
} as const;

/** All of the lawyer's windows. Unpaged; a week has few by nature. */
export async function getMyAvailability(): Promise<AvailabilityResponse[]> {
  const { data } = await api.get<AvailabilityResponse[]>(ENDPOINTS.availability);
  return data;
}

/**
 * Adds a weekly window.
 *
 * Backend validation, both mirrored client-side:
 *   409 "Start time must be before end time."
 *   409 "This availability slot already exists."  (exact day + start + end)
 *
 * Overlapping windows are NOT rejected - only exact duplicates - so a window
 * may legitimately sit inside or across another.
 *
 * Times are `HH:mm` here, matching the DTO's @JsonFormat. Appointments use
 * `HH:mm:ss`.
 */
export async function addAvailability(
  payload: CreateAvailabilityRequest,
): Promise<AvailabilityResponse> {
  const { data } = await api.post<AvailabilityResponse>(
    ENDPOINTS.availability,
    payload,
  );
  return data;
}

/**
 * Removes a window.
 *
 * Returns 404 when the window does not exist or belongs to another lawyer - the
 * API does not distinguish the two, so a 404 means "gone or never yours".
 *
 * Existing appointments are unaffected: they store their own date and time, so
 * removing a window prevents future bookings without disturbing booked ones.
 */
export async function removeAvailability(availabilityId: string): Promise<void> {
  await api.delete(ENDPOINTS.window(availabilityId));
}

export const availabilityService = {
  getMyAvailability,
  addAvailability,
  removeAvailability,
} as const;
