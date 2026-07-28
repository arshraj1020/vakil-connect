package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.lawyer.dto.UpdateLawyerProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface LawyerService {

    LawyerProfileResponse createLawyerProfile(
            String userEmail,
            CreateLawyerProfileRequest request
    );

    /**
     * The profile of the authenticated lawyer, resolved from their email.
     *
     * The counterpart to {@link #updateCurrentLawyerProfile}. Distinct from
     * {@link #getLawyerProfile(UUID)}, which takes a lawyer id: a lawyer has no
     * way to learn their own lawyer id, since it differs from their user id and
     * appears in no authentication response.
     */
    LawyerProfileResponse getCurrentLawyerProfile(String userEmail);

    LawyerProfileResponse updateCurrentLawyerProfile(
            String userEmail,
            UpdateLawyerProfileRequest request
    );

    Page<LawyerSummaryResponse> searchLawyers(
            String keyword,
            String specialization,
            String city,
            BigDecimal minFee,
            BigDecimal maxFee,
            Integer minExperience,
            Double minRating,
            Pageable pageable
    );

    LawyerProfileResponse getLawyerProfile(UUID lawyerId);

    Page<LawyerSummaryResponse> getPendingLawyers(Pageable pageable);

    LawyerProfileResponse verifyLawyer(UUID lawyerId);

}