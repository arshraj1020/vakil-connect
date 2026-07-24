package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.lawyer.dto.AvailabilityResponse;
import com.arshraj.vakilconnect.lawyer.dto.CreateAvailabilityRequest;

import java.util.List;
import java.util.UUID;

public interface AvailabilityService {

    AvailabilityResponse addAvailability(String lawyerEmail, CreateAvailabilityRequest request);

    List<AvailabilityResponse> getOwnAvailability(String lawyerEmail);

    void deleteAvailability(String lawyerEmail, UUID availabilityId);

    List<AvailabilityResponse> getAvailabilityForLawyer(UUID lawyerId);
}
