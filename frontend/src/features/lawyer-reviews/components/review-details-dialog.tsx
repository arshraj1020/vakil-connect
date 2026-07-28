"use client";

import { CalendarDays, Clock, MapPin, User, Video } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { RatingStars } from "@/components/common/rating-stars";
import { StatusBadge } from "@/components/common/status-badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatDateLong, formatTime, formatTimestamp } from "@/lib/date";
import type { AppointmentResponse, ReviewResponse } from "@/types";

import { hasComment } from "../lib/review-utils";

/**
 * Every field the API holds about one review.
 *
 * Reuses the shared `Dialog`, so focus trapping, the escape key, the overlay
 * and scroll locking come from Radix rather than being re-implemented here.
 *
 * The review itself carries only id, appointmentId, clientName, rating, comment
 * and createdAt. Everything under "Consultation" comes from the locally joined
 * appointment and is omitted entirely when that join fails - an absent section
 * is honest, a placeholder date would not be.
 *
 * The appointment's status is shown here rather than on the card. It is always
 * COMPLETED - creation rejects any other status and COMPLETED is terminal - so
 * as a list badge it would carry no information, but in a detail view it
 * usefully confirms the review is attached to a consultation that took place.
 */
export function ReviewDetailsDialog({
  review,
  appointment,
  open,
  onOpenChange,
}: {
  review: ReviewResponse | null;
  appointment: AppointmentResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  if (!review) return null;

  const written = hasComment(review);

  const ModeIcon: LucideIcon =
    appointment?.consultationMode === "ONLINE" ? Video : MapPin;

  const consultationRows: Array<{
    icon: LucideIcon;
    label: string;
    value: string;
  }> = appointment
    ? [
        {
          icon: CalendarDays,
          label: "Date",
          value: formatDateLong(appointment.appointmentDate),
        },
        {
          icon: Clock,
          label: "Time",
          value: formatTime(appointment.appointmentTime),
        },
        {
          icon: ModeIcon,
          label: "Mode",
          value: appointment.consultationMode === "ONLINE" ? "Online" : "In person",
        },
      ]
    : [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Review details</DialogTitle>
          <DialogDescription>
            Left by {review.clientName} on {formatTimestamp(review.createdAt)}.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div className="flex items-center justify-between gap-3">
            <span className="inline-flex items-center gap-2 text-sm text-muted-foreground">
              <User className="size-4" aria-hidden />
              Rating
            </span>
            <RatingStars rating={review.rating} />
          </div>

          <div className="space-y-2 border-t border-border pt-4">
            <p className="text-sm text-muted-foreground">Feedback</p>
            <p
              className={
                written
                  ? "whitespace-pre-line text-sm leading-relaxed"
                  : "text-sm italic text-muted-foreground"
              }
            >
              {written
                ? review.comment
                : "This client rated the consultation without writing a comment."}
            </p>
          </div>

          {appointment ? (
            <div className="space-y-3 border-t border-border pt-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">Consultation</p>
                <StatusBadge status={appointment.status} />
              </div>

              <dl className="space-y-3">
                {consultationRows.map((row) => {
                  const Icon = row.icon;

                  return (
                    <div
                      key={row.label}
                      className="flex items-start justify-between gap-4"
                    >
                      <dt className="inline-flex items-center gap-2 text-sm text-muted-foreground">
                        <Icon className="size-4" aria-hidden />
                        {row.label}
                      </dt>
                      <dd className="text-right text-sm font-medium">
                        {row.value}
                      </dd>
                    </div>
                  );
                })}
              </dl>
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
