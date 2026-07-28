import { CalendarDays, Clock, Video, MapPin } from "lucide-react";

import { StatusBadge } from "@/components/common/status-badge";
import { formatRelativeDay, formatTime } from "@/lib/date";
import { cn } from "@/lib/utils";
import type { AppointmentResponse } from "@/types";

/**
 * A single appointment, as a list row.
 *
 * Shared by the dashboard widgets and, later, the full appointments page, so it
 * takes an appointment and renders it - it fetches nothing and decides nothing
 * about which appointments belong in a list.
 *
 * `perspective` swaps whose name is shown. A client is looking at the lawyer
 * they booked; a lawyer is looking at the client who booked them. Both come
 * from the same DTO, so one component covers both screens rather than two
 * near-identical ones drifting apart.
 */
export function AppointmentCard({
  appointment,
  perspective = "client",
  actions,
  className,
}: {
  appointment: AppointmentResponse;
  perspective?: "client" | "lawyer";
  /** Trailing controls, e.g. Cancel or Accept. Rendered by the caller. */
  actions?: React.ReactNode;
  className?: string;
}) {
  const counterpartyName =
    perspective === "client" ? appointment.lawyerName : appointment.clientName;

  const ModeIcon = appointment.consultationMode === "ONLINE" ? Video : MapPin;

  return (
    <div
      className={cn(
        "flex flex-col gap-3 rounded-xl border border-border p-4 transition-colors",
        "hover:bg-accent/40 sm:flex-row sm:items-center sm:justify-between",
        className,
      )}
    >
      <div className="flex min-w-0 items-start gap-3">
        <span
          className="grid size-10 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
          aria-hidden
        >
          <CalendarDays className="size-4" />
        </span>

        <div className="min-w-0 space-y-1">
          <p className="truncate font-medium">{counterpartyName}</p>

          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Clock className="size-3" aria-hidden />
              {formatRelativeDay(appointment.appointmentDate)} ·{" "}
              {formatTime(appointment.appointmentTime)}
            </span>

            <span className="inline-flex items-center gap-1 capitalize">
              <ModeIcon className="size-3" aria-hidden />
              {appointment.consultationMode.toLowerCase()}
            </span>
          </div>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-3 sm:justify-end">
        <StatusBadge status={appointment.status} />
        {actions}
      </div>
    </div>
  );
}
