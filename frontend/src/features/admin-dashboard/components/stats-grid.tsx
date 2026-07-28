"use client";

import {
  BadgeCheck,
  CalendarDays,
  MessageSquareText,
  Scale,
  ShieldAlert,
  Star,
  UserRound,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { StatCard } from "@/components/common/stat-card";
import { formatNumber, formatRating } from "@/lib/format";
import type { AnalyticsResponse } from "@/types";

/**
 * The platform's headline counts.
 *
 * Every tile is a field of `AnalyticsResponse`, each backed by a server-side
 * COUNT over the whole table - none is sampled, estimated or derived from a
 * page. The only arithmetic is the verification percentage, which is a ratio of
 * two such counts.
 *
 * `StatCard` is the SHARED component from `components/common`, not a
 * feature-local copy: the client and lawyer dashboards already use it, and a
 * second implementation here would be the duplication the brief forbids.
 *
 * Rendered as a list so assistive technology announces the number of statistics
 * and their position, rather than reading eight unrelated blocks of text.
 */
export function StatsGrid({
  analytics,
  verificationRate,
}: {
  analytics: AnalyticsResponse;
  verificationRate: number;
}) {
  const hasLawyerProfiles =
    analytics.verifiedLawyers + analytics.unverifiedLawyers > 0;

  const tiles: Array<{
    label: string;
    value: string;
    icon: LucideIcon;
    hint?: string;
  }> = [
    {
      label: "Total users",
      value: formatNumber(analytics.totalUsers),
      icon: Users,
      hint: `${formatNumber(analytics.totalAdmins)} ${
        analytics.totalAdmins === 1 ? "admin" : "admins"
      }`,
    },
    {
      label: "Clients",
      value: formatNumber(analytics.totalClients),
      icon: UserRound,
    },
    {
      label: "Lawyers",
      value: formatNumber(analytics.totalLawyers),
      icon: Scale,
      hint: hasLawyerProfiles ? `${verificationRate}% verified` : undefined,
    },
    {
      label: "Verified lawyers",
      value: formatNumber(analytics.verifiedLawyers),
      icon: BadgeCheck,
      hint: "Visible in client search",
    },
    {
      label: "Pending verification",
      value: formatNumber(analytics.unverifiedLawyers),
      icon: ShieldAlert,
      hint:
        analytics.unverifiedLawyers > 0
          ? "Awaiting your review"
          : "Queue is clear",
    },
    {
      label: "Appointments",
      value: formatNumber(analytics.totalAppointments),
      icon: CalendarDays,
      hint: `${formatNumber(analytics.completedAppointments)} completed`,
    },
    {
      label: "Reviews",
      value: formatNumber(analytics.totalReviews),
      icon: MessageSquareText,
    },
    {
      /*
       * Named precisely. The backend averages each lawyer's OWN average across
       * lawyers having at least one review - it is not weighted by review
       * count, so it is not the mean review score on the platform. Calling it
       * "average rating" would overstate what it measures.
       */
      label: "Average lawyer rating",
      value:
        analytics.totalReviews > 0
          ? formatRating(analytics.averagePlatformRating)
          : "—",
      icon: Star,
      hint: "Mean of each lawyer's average",
    },
  ];

  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {tiles.map((tile) => (
        <li key={tile.label}>
          <StatCard
            label={tile.label}
            value={tile.value}
            icon={tile.icon}
            hint={tile.hint}
          />
        </li>
      ))}
    </ul>
  );
}
