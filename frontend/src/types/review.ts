import type { IsoDateTime, Uuid } from "./common";

/**
 * Review contracts.
 *
 * Reviews are attached to an appointment, not directly to a lawyer, which is
 * what lets the backend guarantee a review reflects a real consultation.
 */

/**
 * `POST /api/client/appointments/{appointmentId}/review`.
 *
 * Backend rules:
 *  - the appointment must belong to the calling client (otherwise 404),
 *  - its status must be COMPLETED (otherwise 409),
 *  - it must not already have a review (otherwise 409 - one per appointment).
 *
 * Posting a review updates the lawyer's aggregate rating and review count.
 */
export interface CreateReviewRequest {
  /** Integer, 1-5 inclusive. */
  rating: number;
  /** Optional. Max 2000 characters. */
  comment?: string;
}

/** A published review, as shown on a lawyer's public profile. */
export interface ReviewResponse {
  id: Uuid;
  appointmentId: Uuid;
  /** Author's display name; the client's id is not exposed publicly. */
  clientName: string;
  /** 1-5. */
  rating: number;
  comment: string | null;
  createdAt: IsoDateTime;
}
