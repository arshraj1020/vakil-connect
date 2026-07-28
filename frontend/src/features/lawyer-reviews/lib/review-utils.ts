import type { AppointmentResponse, ReviewResponse } from "@/types";

/**
 * Helpers for presenting reviews.
 *
 * No formatting lives here - dates go through `lib/date`, ratings through
 * `lib/format` and `RatingStars`. This file only covers what is specific to
 * reviews: linking a review to its appointment, and describing a rating in
 * words.
 */

/**
 * Indexes the lawyer's appointments by id.
 *
 * `ReviewResponse` carries `appointmentId` but not the appointment's DATE, and
 * no endpoint resolves a single appointment by id. The lawyer's own listing
 * (`GET /api/lawyer/appointments`) is unpaged and therefore complete, so the
 * date can be recovered by a local join rather than invented.
 *
 * Built once per appointments array and memoised by the caller: a Map lookup
 * per review beats scanning the list for each one.
 */
export function indexAppointmentsById(
  appointments: AppointmentResponse[],
): Map<string, AppointmentResponse> {
  return new Map(appointments.map((appointment) => [appointment.id, appointment]));
}

/**
 * The appointment a review belongs to, when it can be resolved.
 *
 * Returns null rather than throwing: the appointments query may still be in
 * flight, or may have failed, and a missing date must degrade to "not shown"
 * instead of breaking the review list. The review itself is always renderable
 * without it.
 */
export function findReviewAppointment(
  review: ReviewResponse,
  byId: Map<string, AppointmentResponse>,
): AppointmentResponse | null {
  return byId.get(review.appointmentId) ?? null;
}

/** Whether the client wrote anything beyond the star rating. `comment` is nullable. */
export function hasComment(review: ReviewResponse): boolean {
  return typeof review.comment === "string" && review.comment.trim().length > 0;
}

/**
 * A rating in words, for screen readers and for the summary card.
 *
 * `RatingStars` already announces "Rated 4 out of 5" on its wrapper, so this is
 * used where the stars are absent or where a card needs a single accessible
 * name that also identifies the reviewer.
 */
export function describeRating(rating: number): string {
  return `${rating} out of 5 stars`;
}
