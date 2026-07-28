import { addDays, format, isValid, parseISO } from "date-fns";

import { todayIso } from "@/lib/date";
import type {
  AppointmentResponse,
  AvailabilityResponse,
  DayOfWeek,
} from "@/types";

/**
 * Turning weekly availability into bookable slots for a specific date.
 *
 * Availability is a RECURRING WEEKLY pattern (`MONDAY 10:00-13:00`), not a set
 * of dates, so a date is bookable when its weekday has a matching window.
 *
 * Slot granularity is a FRONTEND decision. The backend accepts any LocalTime
 * and has no notion of appointment duration, so the interval below defines the
 * grid a client can choose from. Changing it changes nothing server-side.
 */
export const SLOT_INTERVAL_MINUTES = 30;

/** Maps a calendar date to the enum name the backend uses. */
export function dayOfWeekFor(isoDate: string): DayOfWeek | null {
  const parsed = parseISO(isoDate);
  if (!isValid(parsed)) return null;
  return format(parsed, "EEEE").toUpperCase() as DayOfWeek;
}

/** The lawyer's published windows for that date's weekday. */
export function windowsForDate(
  windows: AvailabilityResponse[],
  isoDate: string,
): AvailabilityResponse[] {
  const day = dayOfWeekFor(isoDate);
  if (!day) return [];

  return windows
    .filter((window) => window.available && window.dayOfWeek === day)
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
}

/**
 * Whether a date can be booked at all.
 *
 * Two independent rules, both enforced server-side:
 *  - the backend validates `appointmentDate` with @Future, which rejects TODAY
 *    as well as past dates,
 *  - the weekday must have at least one available window.
 */
export function isDateBookable(
  windows: AvailabilityResponse[],
  isoDate: string,
): boolean {
  if (isoDate <= todayIso()) return false;
  return windowsForDate(windows, isoDate).length > 0;
}

/** Which weekdays this lawyer ever works - used to grey out calendar columns. */
export function bookableWeekdays(
  windows: AvailabilityResponse[],
): Set<DayOfWeek> {
  return new Set(
    windows.filter((window) => window.available).map((window) => window.dayOfWeek),
  );
}

/**
 * Bookable times for a date, as `HH:mm:ss`.
 *
 * The window's opening time is INCLUSIVE and its closing time EXCLUSIVE, which
 * mirrors the backend check `time >= start && time < end`. A 10:00-13:00 window
 * therefore ends at 12:30, not 13:00.
 *
 * Note the format shift: availability windows arrive as `HH:mm` (they carry
 * @JsonFormat), while a booking must submit `HH:mm:ss`.
 */
export function generateSlots(
  windows: AvailabilityResponse[],
  isoDate: string,
  intervalMinutes: number = SLOT_INTERVAL_MINUTES,
): string[] {
  const slots: string[] = [];

  for (const window of windowsForDate(windows, isoDate)) {
    const start = toMinutes(window.startTime);
    const end = toMinutes(window.endTime);
    if (start === null || end === null) continue;

    for (let minute = start; minute < end; minute += intervalMinutes) {
      slots.push(toTimeString(minute));
    }
  }

  // Windows could overlap; de-duplicate and keep chronological order.
  return [...new Set(slots)].sort();
}

/**
 * Times the signed-in client already holds with this lawyer on this date.
 *
 * This is the ONLY slot occupancy the frontend can know about. There is no
 * endpoint exposing another client's bookings - the backend enforces that
 * through a partial unique index and answers with 409 at submit time - so
 * slots taken by other clients cannot be greyed out in advance.
 */
export function slotsTakenByClient(
  appointments: AppointmentResponse[],
  lawyerId: string,
  isoDate: string,
): Set<string> {
  return new Set(
    appointments
      .filter(
        (appointment) =>
          appointment.lawyerId === lawyerId &&
          appointment.appointmentDate === isoDate &&
          (appointment.status === "PENDING" || appointment.status === "ACCEPTED"),
      )
      .map((appointment) => appointment.appointmentTime),
  );
}

/** The earliest date the backend will accept: tomorrow. */
export function earliestBookableDate(): string {
  return format(addDays(new Date(), 1), "yyyy-MM-dd");
}

/* ----------------------------------------------------------------- helpers */

/** "10:30" or "10:30:00" -> minutes since midnight. */
function toMinutes(time: string): number | null {
  const [rawHours, rawMinutes] = time.split(":");
  const hours = Number(rawHours);
  const minutes = Number(rawMinutes ?? "0");

  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  return hours * 60 + minutes;
}

/** minutes since midnight -> "HH:mm:ss", the format a booking must submit. */
function toTimeString(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${pad(hours)}:${pad(minutes)}:00`;
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}
