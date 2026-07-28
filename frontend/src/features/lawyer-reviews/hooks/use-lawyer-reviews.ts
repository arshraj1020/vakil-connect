"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { useMyProfile } from "@/features/lawyer-profile/hooks/use-my-profile";
import { useLawyerAppointments } from "@/features/appointments/hooks/use-lawyer-appointments";
import { queryKeys } from "@/lib/query-keys";
import { lawyerReviewService } from "@/services/lawyer-review-service";
import type { PageParams } from "@/types";

import { indexAppointmentsById } from "../lib/review-utils";

/**
 * The signed-in lawyer's reviews, one page at a time.
 *
 * A CHAINED query, of necessity: the only readable endpoint is the public
 * `GET /api/lawyers/{lawyerId}/reviews`, and a lawyer learns their own lawyerId
 * only from `GET /api/lawyer/profile`. The reviews query stays disabled until
 * that id arrives, so no request is fired against `/api/lawyers/undefined`.
 *
 * `keepPreviousData` holds the current page on screen while the next one loads,
 * which stops the list collapsing to a skeleton on every page change.
 *
 * The appointments query is reused, not re-fetched: Group 2 already populates
 * `appointments.lawyer()`, and this hook reads the same cache entry to recover
 * each review's appointment date. It is treated as strictly optional - reviews
 * render fully without it.
 *
 * Both `queryKeys.lawyers.myProfile()` and `queryKeys.lawyers.reviews()` already
 * existed; no new key shape was introduced.
 */
export function useLawyerReviews(params: PageParams) {
  const profileQuery = useMyProfile();
  const lawyerId = profileQuery.profile?.id;

  const reviewsQuery = useQuery({
    queryKey: queryKeys.lawyers.reviews(lawyerId ?? "", params),
    queryFn: () => lawyerReviewService.getMyReviews(lawyerId ?? "", params),
    enabled: Boolean(lawyerId),
    placeholderData: keepPreviousData,
  });

  /*
   * Optional enrichment. A failure here is deliberately not surfaced as an
   * error state: it costs the appointment date on each card and nothing else.
   */
  const appointmentsQuery = useLawyerAppointments();

  const appointmentsById = useMemo(
    () => indexAppointmentsById(appointmentsQuery.appointments),
    [appointmentsQuery.appointments],
  );

  /*
   * Read straight from the page - NOT sorted here. The backend orders by
   * `createdAt DESC` in the repository method itself, so re-sorting client-side
   * would at best duplicate that and at worst reorder a page against the
   * pagination it belongs to.
   */
  const reviews = useMemo(
    () => reviewsQuery.data?.content ?? [],
    [reviewsQuery.data],
  );

  const page = reviewsQuery.data?.page;

  return {
    reviews,
    appointmentsById,

    /** Authoritative aggregates, straight from the profile - never derived. */
    averageRating: profileQuery.profile?.rating ?? 0,
    totalReviews: profileQuery.profile?.totalReviews ?? 0,

    totalPages: page?.totalPages ?? 0,
    totalElements: page?.totalElements ?? 0,

    /*
     * Pending only while something is genuinely unknown. The profile must load
     * before reviews can even be requested, so its pending state counts too;
     * `isPlaceholderData` is excluded so paging does not blank the list.
     */
    isPending: profileQuery.isPending || (Boolean(lawyerId) && reviewsQuery.isPending),
    isFetching: reviewsQuery.isFetching,

    /* Either link in the chain failing means the page cannot be shown. */
    isError: profileQuery.isError || reviewsQuery.isError,
    error: profileQuery.error ?? reviewsQuery.error,

    /** The account has no lawyer profile at all - distinct from a failed load. */
    isMissingProfile: profileQuery.isMissingProfile,

    refetch: () => {
      void profileQuery.refetch();
      void reviewsQuery.refetch();
    },
  };
}
