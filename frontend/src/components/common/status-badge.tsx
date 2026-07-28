import { Badge } from "@/components/ui/badge";
import { getStatusMeta } from "@/lib/status";
import { cn } from "@/lib/utils";
import type { AppointmentStatus } from "@/types";

/**
 * Renders an appointment status.
 *
 * Holds no colour or copy of its own: label, intent and icon all come from
 * `APPOINTMENT_STATUS_META`, so a status looks and reads identically wherever
 * it appears, and changing its presentation is a one-line edit in that map.
 */
export function StatusBadge({
  status,
  showIcon = true,
  className,
}: {
  status: AppointmentStatus;
  showIcon?: boolean;
  className?: string;
}) {
  const { label, intent, icon: Icon } = getStatusMeta(status);

  return (
    <Badge variant={intent} className={cn(className)}>
      {showIcon ? <Icon aria-hidden /> : null}
      {label}
    </Badge>
  );
}
