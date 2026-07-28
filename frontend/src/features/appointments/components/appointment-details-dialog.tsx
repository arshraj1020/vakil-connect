"use client";

import { CalendarDays, Clock, MapPin, StickyNote, User, Video } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { StatusBadge } from "@/components/common/status-badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatDateLong, formatTime, formatTimestamp } from "@/lib/date";
import { getStatusMeta } from "@/lib/status";
import type { AppointmentResponse } from "@/types";

/**
 * Everything the API knows about one appointment.
 *
 * The row shows what is needed to scan a list; this shows the rest - notes,
 * consultation mode, when the request was made - without navigating away, since
 * there is no per-appointment endpoint or route to navigate to.
 *
 * The status description comes from the shared status map, so the explanation
 * of what "Pending" means is written once rather than per surface.
 */
export function AppointmentDetailsDialog({
  appointment,
  open,
  onOpenChange,
  perspective = "client",
}: {
  appointment: AppointmentResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /**
   * Whose counterpart to show, mirroring AppointmentCard. A client is looking
   * at the lawyer they booked; a lawyer is looking at the client who booked
   * them. One dialog serves both rather than two that drift apart.
   */
  perspective?: "client" | "lawyer";
}) {
  if (!appointment) return null;

  const meta = getStatusMeta(appointment.status);
  const ModeIcon: LucideIcon =
    appointment.consultationMode === "ONLINE" ? Video : MapPin;

  const isClientView = perspective === "client";

  const rows: Array<{ icon: LucideIcon; label: string; value: string }> = [
    {
      icon: User,
      label: isClientView ? "Lawyer" : "Client",
      value: isClientView ? appointment.lawyerName : appointment.clientName,
    },
    {
      icon: CalendarDays,
      label: "Date",
      value: formatDateLong(appointment.appointmentDate),
    },
    { icon: Clock, label: "Time", value: formatTime(appointment.appointmentTime) },
    {
      icon: ModeIcon,
      label: "Mode",
      value: appointment.consultationMode === "ONLINE" ? "Online" : "In person",
    },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Appointment details</DialogTitle>
          <DialogDescription>{meta.description}</DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-muted-foreground">Status</span>
            <StatusBadge status={appointment.status} />
          </div>

          <dl className="space-y-3">
            {rows.map((row) => {
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
                  <dd className="text-right text-sm font-medium">{row.value}</dd>
                </div>
              );
            })}
          </dl>

          {appointment.notes ? (
            <div className="space-y-2 border-t border-border pt-4">
              <p className="inline-flex items-center gap-2 text-sm text-muted-foreground">
                <StickyNote className="size-4" aria-hidden />
                {isClientView ? "Your notes" : "Notes from the client"}
              </p>
              <p className="whitespace-pre-line text-sm leading-relaxed">
                {appointment.notes}
              </p>
            </div>
          ) : null}

          <p className="border-t border-border pt-4 text-xs text-muted-foreground">
            Requested on {formatTimestamp(appointment.createdAt)}
          </p>
        </div>
      </DialogContent>
    </Dialog>
  );
}
