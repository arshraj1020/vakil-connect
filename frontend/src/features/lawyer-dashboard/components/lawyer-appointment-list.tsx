"use client";

import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { AppointmentCard } from "@/features/appointments/components/appointment-card";
import type { AppointmentResponse } from "@/types";

/**
 * A titled card listing appointments, with the full state cycle handled once.
 *
 * Today and Upcoming differ only in heading, empty copy and which derived array
 * they receive, so the loading/error/empty/data handling lives here rather than
 * being written twice. Mirrors the client dashboard's equivalent wrapper.
 *
 * Presentational: it fetches nothing. Each row renders the shared
 * AppointmentCard with `perspective="lawyer"`, so the client's name is shown
 * rather than the lawyer's own.
 */
export function LawyerAppointmentList({
  title,
  description,
  appointments,
  isPending,
  isError,
  error,
  onRetry,
  onViewDetails,
  emptyIcon,
  emptyTitle,
  emptyDescription,
  viewAllHref,
  limit = 5,
}: {
  title: string;
  description?: string;
  appointments: AppointmentResponse[];
  isPending: boolean;
  isError: boolean;
  error?: unknown;
  onRetry: () => void;
  onViewDetails: (appointment: AppointmentResponse) => void;
  emptyIcon?: LucideIcon;
  emptyTitle: string;
  emptyDescription?: string;
  viewAllHref?: string;
  limit?: number;
}): ReactNode {
  const visible = appointments.slice(0, limit);
  const hasMore = appointments.length > limit;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
        <div className="space-y-1.5">
          <CardTitle>{title}</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </div>

        {viewAllHref && hasMore ? (
          <Link
            href={viewAllHref}
            className="shrink-0 text-sm font-medium text-primary underline-offset-4 hover:underline"
          >
            View all
          </Link>
        ) : null}
      </CardHeader>

      <CardContent>
        {isPending ? (
          <ListSkeleton count={3} />
        ) : isError ? (
          <ErrorState
            error={error}
            onRetry={onRetry}
            title="Could not load appointments"
          />
        ) : visible.length === 0 ? (
          <EmptyState
            icon={emptyIcon}
            title={emptyTitle}
            description={emptyDescription}
          />
        ) : (
          <div className="space-y-3">
            {visible.map((appointment) => (
              <AppointmentCard
                key={appointment.id}
                appointment={appointment}
                perspective="lawyer"
                actions={
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onViewDetails(appointment)}
                  >
                    Details
                  </Button>
                }
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
