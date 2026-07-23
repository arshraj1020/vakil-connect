package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface LawyerService {

    LawyerProfileResponse createLawyerProfile(
            String userEmail,
            CreateLawyerProfileRequest request
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