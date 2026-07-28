import {
  Ban,
  CalendarCheck,
  CheckCircle2,
  Clock,
  XCircle,
  type LucideIcon,
} from "lucide-react";

import type { AppointmentStatus } from "@/types";

/**
 * Semantic presentation for appointment statuses.
 *
 * The single place a status is turned into a label, a colour intent and an
 * icon. Components read from here and never hardcode either copy or colour, so
 * a status renders identically in a badge, a filter chip, a dashboard tile and
 * a timeline.
 *
 * Typed as a total `Record`, which makes the mapping exhaustive: if the backend
 * ever adds a status to `AppointmentStatus`, this file fails to compile until
 * the new case is handled - far better than silently rendering it unstyled.
 */

/** Colour intents available on Badge, expressed semantically rather than by hue. */
export type StatusIntent =
  | "default"
  | "secondary"
  | "success"
  | "warning"
  | "destructive"
  | "info";

export interface StatusMeta {
  /** Human-facing label. Sentence case, never the raw enum name. */
  label: string;
  intent: StatusIntent;
  icon: LucideIcon;
  /** Short explanation, suitable for tooltips or empty-state copy. */
  description: string;
}

export const APPOINTMENT_STATUS_META: Record<AppointmentStatus, StatusMeta> = {
  PENDING: {
    label: "Pending",
    intent: "warning",
    icon: Clock,
    description: "Waiting for the lawyer to respond.",
  },
  ACCEPTED: {
    label: "Confirmed",
    intent: "info",
    icon: CalendarCheck,
    description: "The lawyer has accepted this consultation.",
  },
  COMPLETED: {
    label: "Completed",
    intent: "success",
    icon: CheckCircle2,
    description: "The consultation took place.",
  },
  REJECTED: {
    label: "Declined",
    intent: "destructive",
    icon: XCircle,
    description: "The lawyer was unable to take this consultation.",
  },
  CANCELLED: {
    label: "Cancelled",
    intent: "secondary",
    icon: Ban,
    description: "This consultation was cancelled.",
  },
};

export function getStatusMeta(status: AppointmentStatus): StatusMeta {
  return APPOINTMENT_STATUS_META[status];
}

/**
 * Whether a status still occupies the lawyer's slot.
 *
 * Mirrors the backend's partial unique index, which covers PENDING and
 * ACCEPTED only - terminal statuses release the slot for rebooking.
 */
export function isActiveStatus(status: AppointmentStatus): boolean {
  return status === "PENDING" || status === "ACCEPTED";
}

/** Whether the client is still allowed to cancel, mirroring the service rules. */
export function isCancellable(status: AppointmentStatus): boolean {
  return isActiveStatus(status);
}

/** Whether a completed consultation is eligible for a review. */
export function isReviewable(status: AppointmentStatus): boolean {
  return status === "COMPLETED";
}

/* --------------------------------------------------- lawyer transitions --
 * These mirror AppointmentServiceImpl exactly. Keeping them beside the client
 * rules means every transition precondition in the product is defined in one
 * file, and a component never re-derives one from a status comparison.
 */

/** Accept is permitted from PENDING only; anything else answers 409. */
export function isAcceptable(status: AppointmentStatus): boolean {
  return status === "PENDING";
}

/** Reject is permitted from PENDING only. */
export function isRejectable(status: AppointmentStatus): boolean {
  return status === "PENDING";
}

/** Complete is permitted from ACCEPTED only - a pending appointment cannot skip ahead. */
export function isCompletable(status: AppointmentStatus): boolean {
  return status === "ACCEPTED";
}

/** Whether a lawyer has any available action for this status. */
export function hasLawyerActions(status: AppointmentStatus): boolean {
  return isAcceptable(status) || isRejectable(status) || isCompletable(status);
}
