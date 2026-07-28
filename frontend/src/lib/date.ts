import { format, isValid, parseISO } from "date-fns";

/**
 * Formatting for the backend's date and time wire formats.
 *
 * Three distinct shapes arrive from the API and each needs different handling:
 *   LocalDate      "2026-08-03"
 *   LocalTime      "10:30:00"   (availability windows use "10:00")
 *   LocalDateTime  "2026-07-27T19:15:04.795"
 *
 * None carry a timezone. They are wall-clock values in the lawyer's locale, so
 * they are formatted as written and never converted - running "2026-08-03"
 * through a UTC-aware conversion can shift it a day in either direction.
 */

/** Today as an ISO date string, for comparison against `appointmentDate`. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}

/**
 * Compares ISO date strings directly.
 *
 * Lexicographic comparison is exact for ISO-8601 dates, which avoids
 * constructing Date objects and the timezone shifts that come with them.
 */
export function isOnOrAfterToday(isoDate: string): boolean {
  return isoDate >= todayIso();
}

/** "3 Aug 2026" */
export function formatDate(isoDate: string): string {
  const parsed = parseISO(isoDate);
  return isValid(parsed) ? format(parsed, "d MMM yyyy") : isoDate;
}

/** "Mon, 3 Aug 2026" */
export function formatDateLong(isoDate: string): string {
  const parsed = parseISO(isoDate);
  return isValid(parsed) ? format(parsed, "EEE, d MMM yyyy") : isoDate;
}

/**
 * "Today" / "Tomorrow" / "Mon, 3 Aug" - a relative label for list rows.
 *
 * Compared as strings for the same reason as `isOnOrAfterToday`.
 */
export function formatRelativeDay(isoDate: string): string {
  const today = todayIso();
  if (isoDate === today) return "Today";

  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (isoDate === format(tomorrow, "yyyy-MM-dd")) return "Tomorrow";

  const parsed = parseISO(isoDate);
  return isValid(parsed) ? format(parsed, "EEE, d MMM") : isoDate;
}

/**
 * "10:30 AM" from either "10:30:00" or "10:30".
 *
 * Parsed by hand rather than via a Date: a bare time has no date component, so
 * anchoring it to one only to format it invites off-by-one-day bugs.
 */
export function formatTime(time: string): string {
  const [rawHours, rawMinutes] = time.split(":");
  const hours = Number(rawHours);
  const minutes = rawMinutes ?? "00";

  if (!Number.isFinite(hours)) return time;

  const period = hours >= 12 ? "PM" : "AM";
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;

  return `${hour12}:${minutes} ${period}`;
}

/** "Mon, 3 Aug 2026 at 10:30 AM" */
export function formatDateTime(isoDate: string, time: string): string {
  return `${formatDateLong(isoDate)} at ${formatTime(time)}`;
}

/** "27 Jul 2026" from a LocalDateTime such as createdAt. */
export function formatTimestamp(isoDateTime: string): string {
  const parsed = parseISO(isoDateTime);
  return isValid(parsed) ? format(parsed, "d MMM yyyy") : isoDateTime;
}
