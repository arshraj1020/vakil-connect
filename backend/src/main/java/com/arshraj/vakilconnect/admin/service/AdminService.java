package com.arshraj.vakilconnect.admin.service;

import com.arshraj.vakilconnect.admin.dto.AdminReviewResponse;
import com.arshraj.vakilconnect.admin.dto.AnalyticsResponse;
import com.arshraj.vakilconnect.admin.dto.UserSummaryResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.user.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminService {

    Page<LawyerSummaryResponse> getPendingLawyers(Pageable pageable);

    LawyerProfileResponse verifyLawyer(UUID lawyerId);

    LawyerProfileResponse rejectLawyer(UUID lawyerId, String reason);

    Page<UserSummaryResponse> getUsers(Role role, Pageable pageable);

    UserSummaryResponse setUserActive(UUID userId, boolean active);

    /**
     * Permanently deletes a user account and everything that belongs to it
     * (lawyer profile, appointments, reviews, AI documents - see V10's
     * cascading FKs). Irreversible.
     *
     * @param requesterEmail the authenticated admin making the call, used to
     *                        refuse self-deletion and to identify the actor
     *                        for the last-admin guard.
     */
    void deleteUser(UUID userId, String requesterEmail);

    Page<AdminReviewResponse> getReviews(Pageable pageable);

    void deleteReview(UUID reviewId);

    AnalyticsResponse getAnalytics();
}
