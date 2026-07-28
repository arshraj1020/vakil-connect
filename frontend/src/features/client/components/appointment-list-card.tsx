"use client";

import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
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
 * The upcoming and recent widgets differ only in their heading, empty copy and
 * which derived array they receive - everything else (loading, error, empty,
 * data) is identical, so it lives here rather than being written twice.
 *
 * Deliberately presentational: it fetches nothing. The caller decides which
 * appointments belong in it, which keeps the same card usable for any future
 * filtered list.
 */
export function AppointmentListCard({
  title,
  description,
  appointments,
  isPending,
  isError,
  error,
  onRetry,
  emptyIcon,
  emptyTitle,
  emptyDescription,
  emptyAction,
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
  emptyIcon?: LucideIcon;
  emptyTitle: string;
  emptyDescription?: string;
  emptyAction?: ReactNode;
  viewAllHref?: string;
  limit?: number;
}) {
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
            action={emptyAction}
          />
        ) : (
          <div className="space-y-3">
            {visible.map((appointment) => (
              <AppointmentCard
                key={appointment.id}
                appointment={appointment}
                perspective="client"
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
