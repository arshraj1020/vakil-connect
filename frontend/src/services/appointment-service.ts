import api from "@/lib/axios";
import type {
  AppointmentResponse,
  BookAppointmentRequest,
  ClientDashboardResponse,
  LawyerDashboardResponse,
} from "@/types";

/**
 * Client-side appointment endpoints.
 *
 * Scoped to what the dashboard needs; booking and cancellation are added when
 * those features are built, so nothing here is dead code.
 *
 * Identity comes from the JWT: neither endpoint accepts a user id, so a client
 * can only ever read their own appointments.
 */

const ENDPOINTS = {
  clientAppointments: "/api/client/appointments",
  clientDashboard: "/api/client/dashboard",
  cancelAppointment: (appointmentId: string) =>
    `/api/client/appointments/${appointmentId}/cancel`,
  lawyerAppointments: "/api/lawyer/appointments",
  lawyerDashboard: "/api/lawyer/dashboard",
  lawyerAppointmentAction: (appointmentId: string, action: LawyerAppointmentAction) =>
    `/api/lawyer/appointments/${appointmentId}/${action}`,
} as const;

/**
 * The status transitions a lawyer can perform.
 *
 * The value doubles as the URL segment, which is why it is a union of the exact
 * backend paths rather than a separate mapping that could drift.
 */
export type LawyerAppointmentAction = "accept" | "reject" | "complete";

/**
 * Every appointment belonging to the signed-in client.
 *
 * Returns a plain array, not a page - the backend does not paginate this
 * listing. Ordering is appointmentDate DESC, then appointmentTime DESC, so the
 * furthest-future appointment comes first and the oldest last.
 */
export async function getClientAppointments(): Promise<AppointmentResponse[]> {
  const { data } = await api.get<AppointmentResponse[]>(
    ENDPOINTS.clientAppointments,
  );
  return data;
}

/**
 * Aggregate counts plus the single nearest upcoming appointment.
 *
 * `nextAppointment` is null when the client has nothing scheduled. The counts
 * are computed server-side, so they are authoritative - the dashboard never
 * derives them from the appointment list.
 */
export async function getClientDashboard(): Promise<ClientDashboardResponse> {
  const { data } = await api.get<ClientDashboardResponse>(
    ENDPOINTS.clientDashboard,
  );
  return data;
}

/**
 * Books a consultation.
 *
 * Every rejection the backend can return is deterministic and worth surfacing
 * distinctly:
 *   404 - lawyer id does not exist
 *   409 - lawyer unverified, requested time outside their availability, or the
 *         slot was taken between loading the page and submitting
 *   400 - validation, including a date that is not in the future
 *
 * The 409 for a taken slot is enforced by a database-level partial unique
 * index, so it is authoritative even under concurrent requests.
 */
export async function bookAppointment(
  payload: BookAppointmentRequest,
): Promise<AppointmentResponse> {
  const { data } = await api.post<AppointmentResponse>(
    ENDPOINTS.clientAppointments,
    payload,
  );
  return data;
}

/**
 * Cancels one of the client's own appointments.
 *
 * Permitted from PENDING and ACCEPTED only; the backend answers 409 for a
 * terminal appointment ("This appointment can no longer be cancelled.").
 *
 * Ownership is enforced by querying with the caller as a predicate, so an
 * appointment belonging to someone else returns 404 rather than 403 - the API
 * does not disclose that it exists. A 404 here therefore means "gone or never
 * yours", not "forbidden".
 */
export async function cancelAppointment(
  appointmentId: string,
): Promise<AppointmentResponse> {
  const { data } = await api.put<AppointmentResponse>(
    ENDPOINTS.cancelAppointment(appointmentId),
  );
  return data;
}

/**
 * Every appointment belonging to the signed-in lawyer.
 *
 * Unpaged, like the client listing, and sorted appointmentDate DESC then
 * appointmentTime DESC.
 */
export async function getLawyerAppointments(): Promise<AppointmentResponse[]> {
  const { data } = await api.get<AppointmentResponse[]>(
    ENDPOINTS.lawyerAppointments,
  );
  return data;
}

/**
 * Lawyer dashboard statistics.
 *
 * Reports pending, accepted, completed and today's counts, plus verification
 * status and review aggregates. Note it does NOT report rejected or cancelled
 * counts, so a grand total cannot be assembled from this response alone.
 */
export async function getLawyerDashboard(): Promise<LawyerDashboardResponse> {
  const { data } = await api.get<LawyerDashboardResponse>(
    ENDPOINTS.lawyerDashboard,
  );
  return data;
}

/**
 * Moves an appointment to its next status.
 *
 * Preconditions enforced by the backend, each answering 409 when unmet:
 *   accept   - from PENDING only
 *   reject   - from PENDING only
 *   complete - from ACCEPTED only
 *
 * Ownership is enforced by querying with the caller as a predicate, so an
 * appointment belonging to another lawyer returns 404, not 403.
 */
export async function updateLawyerAppointmentStatus(
  appointmentId: string,
  action: LawyerAppointmentAction,
): Promise<AppointmentResponse> {
  const { data } = await api.put<AppointmentResponse>(
    ENDPOINTS.lawyerAppointmentAction(appointmentId, action),
  );
  return data;
}

export const appointmentService = {
  getClientAppointments,
  getClientDashboard,
  bookAppointment,
  cancelAppointment,
  getLawyerAppointments,
  getLawyerDashboard,
  updateLawyerAppointmentStatus,
} as const;
