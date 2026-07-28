import { isOnOrAfterToday, todayIso } from "@/lib/date";
import { isActiveStatus } from "@/lib/status";
import type { AppointmentResponse } from "@/types";

/**
 * Derivations over a lawyer's appointment list.
 *
 * Pure functions, so they are trivially testable and can be memoised by the
 * caller without carrying hook machinery.
 *
 * Predicates mirror the backend's own so a widget can never contradict the
 * statistic printed above it:
 *   today's  -> date == today AND status in (PENDING, ACCEPTED)
 *              matches countByLawyerAndAppointmentDateAndStatusIn
 *   upcoming -> date  > today AND status in (PENDING, ACCEPTED)
 *
 * Dates are compared as ISO strings. That is exact for ISO-8601 and avoids the
 * timezone shift that comes from parsing a LocalDate into a JS Date.
 */

/** Ascending by date, then time - the order a working day is read in. */
function compareChronologically(
  a: AppointmentResponse,
  b: AppointmentResponse,
): number {
  if (a.appointmentDate !== b.appointmentDate) {
    return a.appointmentDate < b.appointmentDate ? -1 : 1;
  }
  if (a.appointmentTime === b.appointmentTime) return 0;
  return a.appointmentTime < b.appointmentTime ? -1 : 1;
}

/** Active appointments dated today, soonest first. */
export function selectTodaysAppointments(
  appointments: AppointmentResponse[],
): AppointmentResponse[] {
  const today = todayIso();

  return appointments
    .filter(
      (appointment) =>
        appointment.appointmentDate === today &&
        isActiveStatus(appointment.status),
    )
    .sort(compareChronologically);
}

/**
 * Active appointments dated after today, soonest first.
 *
 * Excludes today so it complements the today widget rather than repeating it.
 */
export function selectUpcomingAppointments(
  appointments: AppointmentResponse[],
): AppointmentResponse[] {
  const today = todayIso();

  return appointments
    .filter(
      (appointment) =>
        appointment.appointmentDate > today &&
        isActiveStatus(appointment.status),
    )
    .sort(compareChronologically);
}

/**
 * Everything already dated in the past, or in a terminal state.
 *
 * Not shown on the dashboard, but derived here so Appointment Management reuses
 * the same definition instead of writing its own.
 */
export function selectPastAppointments(
  appointments: AppointmentResponse[],
): AppointmentResponse[] {
  return appointments.filter(
    (appointment) =>
      !isOnOrAfterToday(appointment.appointmentDate) ||
      !isActiveStatus(appointment.status),
  );
}

/**
 * Total appointments of every status.
 *
 * Derived rather than read from the dashboard endpoint, which reports only
 * pending, accepted and completed - a lawyer's rejected and cancelled
 * appointments are absent, so those counts cannot be summed into a true total.
 * The list endpoint is unpaged and already fetched for the widgets, so this
 * costs no additional request.
 */
export function selectTotalAppointments(
  appointments: AppointmentResponse[],
): number {
  return appointments.length;
}
