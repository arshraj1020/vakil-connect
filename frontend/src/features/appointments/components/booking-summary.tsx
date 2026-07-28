import { CalendarDays, Clock, MapPin, Video } from "lucide-react";

import { InitialsAvatar } from "@/components/common/initials-avatar";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatDateLong, formatTime } from "@/lib/date";
import { formatCurrency } from "@/lib/format";
import type { ConsultationMode, LawyerProfileResponse } from "@/types";

/**
 * Running summary of the booking.
 *
 * Shows the fee prominently because it is the one commitment a client is
 * making - there is no payment step in v1, so this is where the amount has to
 * be unambiguous before confirming.
 *
 * Unselected values render as placeholders rather than being hidden, so the
 * card keeps a constant height and the layout does not jump as choices are
 * made.
 */
export function BookingSummary({
  lawyer,
  date,
  time,
  mode,
}: {
  lawyer: LawyerProfileResponse;
  date: string;
  time: string;
  mode: ConsultationMode | undefined;
}) {
  const ModeIcon = mode === "OFFLINE" ? MapPin : Video;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Booking summary</CardTitle>
      </CardHeader>

      <CardContent className="space-y-5">
        <div className="flex items-center gap-3">
          <InitialsAvatar name={lawyer.fullName} size="sm" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{lawyer.fullName}</p>
            <p className="truncate text-xs text-muted-foreground">{lawyer.city}</p>
          </div>
        </div>

        <dl className="space-y-3 text-sm">
          <div className="flex items-start justify-between gap-3">
            <dt className="inline-flex items-center gap-2 text-muted-foreground">
              <CalendarDays className="size-4" aria-hidden />
              Date
            </dt>
            <dd className={date ? "font-medium" : "text-muted-foreground"}>
              {date ? formatDateLong(date) : "Not selected"}
            </dd>
          </div>

          <div className="flex items-start justify-between gap-3">
            <dt className="inline-flex items-center gap-2 text-muted-foreground">
              <Clock className="size-4" aria-hidden />
              Time
            </dt>
            <dd className={time ? "font-medium" : "text-muted-foreground"}>
              {time ? formatTime(time) : "Not selected"}
            </dd>
          </div>

          <div className="flex items-start justify-between gap-3">
            <dt className="inline-flex items-center gap-2 text-muted-foreground">
              <ModeIcon className="size-4" aria-hidden />
              Mode
            </dt>
            <dd className={mode ? "font-medium capitalize" : "text-muted-foreground"}>
              {mode ? mode.toLowerCase() : "Not selected"}
            </dd>
          </div>
        </dl>

        <div className="flex items-baseline justify-between border-t border-border pt-4">
          <span className="text-sm text-muted-foreground">Consultation fee</span>
          <span className="text-lg font-semibold">
            {formatCurrency(lawyer.consultationFee)}
          </span>
        </div>

        <p className="text-xs text-muted-foreground">
          Payment is arranged directly with the lawyer. Your request is sent for
          confirmation and is not booked until they accept.
        </p>
      </CardContent>
    </Card>
  );
}
