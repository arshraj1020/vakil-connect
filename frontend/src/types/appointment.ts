import type { IsoDate, IsoDateTime, IsoTime, Uuid } from "./common";

/**
 * Appointment lifecycle and dashboard contracts.
 */

/**
 * Appointment states.
 *
 * Transitions implemented by the backend:
 *
 *   PENDING  -> ACCEPTED   (lawyer accepts)
 *   PENDING  -> REJECTED   (lawyer rejects)   [terminal]
 *   PENDING  -> CANCELLED  (client cancels)   [terminal]
 *   ACCEPTED -> COMPLETED  (lawyer completes) [terminal]
 *   ACCEPTED -> CANCELLED  (client cancels)   [terminal]
 *
 * Any other transition is rejected with 409. There is no reschedule operation:
 * changing a slot means cancelling and booking again.
 */
export type AppointmentStatus =
  | "PENDING"
  | "ACCEPTED"
  | "REJECTED"
  | "COMPLETED"
  | "CANCELLED";

/** Statuses that still occupy a lawyer's time slot. */
export const ACTIVE_APPOINTMENT_STATUSES = ["PENDING", "ACCEPTED"] as const;

/** Statuses from which no further transition is possible. */
export const TERMINAL_APPOINTMENT_STATUSES = [
  "REJECTED",
  "COMPLETED",
  "CANCELLED",
] as const;

export type ConsultationMode = "ONLINE" | "OFFLINE";

/**
 * `POST /api/client/appointments`.
 *
 * Backend rules that the UI must respect to avoid predictable failures:
 *  - `appointmentDate` is validated with `@Future`, which rejects TODAY as well
 *    as past dates. Disable today in any date picker.
 *  - the time must fall inside one of the lawyer's availability windows for
 *    that weekday (start inclusive, end exclusive).
 *  - the lawyer must be admin-verified.
 *  - the slot must be free; a duplicate active booking is rejected with 409 by
 *    a database-level partial unique index.
 */
export interface BookAppointmentRequest {
  lawyerId: Uuid;
  /** `YYYY-MM-DD`. Must be strictly after today. */
  appointmentDate: IsoDate;
  /** `HH:mm:ss` - note availability windows are exposed as `HH:mm`. */
  appointmentTime: IsoTime;
  consultationMode: ConsultationMode;
  /** Optional. Max 2000 characters. */
  notes?: string;
}

/** An appointment as returned by every appointment endpoint. */
export interface AppointmentResponse {
  id: Uuid;

  lawyerId: Uuid;
  lawyerName: string;

  /** The client's `User` id (not a separate client-profile id). */
  clientId: Uuid;
  clientName: string;

  appointmentDate: IsoDate;
  /** `HH:mm:ss`. */
  appointmentTime: IsoTime;
  consultationMode: ConsultationMode;
  status: AppointmentStatus;
  notes: string | null;
  createdAt: IsoDateTime;
}

/**
 * `GET /api/client/dashboard`.
 *
 * "Upcoming" counts appointments dated today or later whose status is PENDING
 * or ACCEPTED.
 */
export interface ClientDashboardResponse {
  totalAppointments: number;
  upcomingAppointments: number;
  completedAppointments: number;
  cancelledAppointments: number;
  /** Nearest upcoming appointment, or `null` when there is none. */
  nextAppointment: AppointmentResponse | null;
}

/**
 * `GET /api/lawyer/dashboard`.
 *
 * `todaysAppointments` counts only active (PENDING/ACCEPTED) appointments dated
 * today. `averageRating` and `totalReviews` are read from the lawyer record,
 * which the backend maintains as reviews are added or removed.
 */
export interface LawyerDashboardResponse {
  pendingAppointments: number;
  acceptedAppointments: number;
  completedAppointments: number;
  todaysAppointments: number;

  profileVerified: boolean;
  averageRating: number;
  totalReviews: number;
}
