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

    Page<UserSummaryResponse> getUsers(Role role, Pageable pageable);

    UserSummaryResponse setUserActive(UUID userId, boolean active);

    Page<AdminReviewResponse> getReviews(Pageable pageable);

    void deleteReview(UUID reviewId);

    AnalyticsResponse getAnalytics();
}
