package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;

public interface LawyerService {

    LawyerProfileResponse createLawyerProfile(
            String userEmail,
            CreateLawyerProfileRequest request
    );

}