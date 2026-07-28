import type { IsoDateTime, PageParams, Uuid } from "./common";
import type { Role } from "./auth";

/**
 * Administration contracts.
 *
 * Every endpoint below requires ROLE_ADMIN. Note that admins are deliberately
 * NOT able to reach client or lawyer endpoints - those return 403 - so admin
 * screens must be built exclusively from the routes typed here.
 */

/** A user account as listed in admin user management. */
export interface UserSummaryResponse {
  id: Uuid;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  /** Deactivated users are rejected at authentication and cannot log in. */
  active: boolean;
  createdAt: IsoDateTime;
}

/** Filters for `GET /api/admin/users`. */
export interface AdminUserParams extends PageParams {
  /** Omit to list every role. */
  role?: Role;
}

/**
 * A review in the moderation queue.
 *
 * Richer than the public `ReviewResponse`: it also names the lawyer, so a
 * moderator has both sides of the interaction without a second request.
 *
 * Deleting a review recalculates the lawyer's aggregate rating.
 */
export interface AdminReviewResponse {
  id: Uuid;
  appointmentId: Uuid;
  clientName: string;
  lawyerName: string;
  /** 1-5. */
  rating: number;
  comment: string | null;
  createdAt: IsoDateTime;
}

/**
 * `GET /api/admin/dashboard`.
 *
 * The backend also serves this identical payload at `GET /api/admin/analytics`
 * - both controller methods delegate to the same `adminService.getAnalytics()`.
 * The frontend calls only `/api/admin/dashboard`, so the resource has exactly
 * one service method and one cache entry.
 */
export interface AnalyticsResponse {
  totalUsers: number;
  totalClients: number;
  totalLawyers: number;
  totalAdmins: number;

  verifiedLawyers: number;
  /** Awaiting admin verification - the pending queue. */
  unverifiedLawyers: number;

  totalAppointments: number;
  pendingAppointments: number;
  acceptedAppointments: number;
  completedAppointments: number;
  rejectedAppointments: number;
  cancelledAppointments: number;

  totalReviews: number;
  /** Mean rating across lawyers having at least one review. 2 decimals. */
  averagePlatformRating: number;
}
