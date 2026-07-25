package com.arshraj.vakilconnect.lawyer.controller;

import com.arshraj.vakilconnect.appointment.dto.LawyerDashboardResponse;
import com.arshraj.vakilconnect.appointment.service.AppointmentService;
import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.UpdateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.service.LawyerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LawyerController {

    private final LawyerService lawyerService;
    private final AppointmentService appointmentService;

    public LawyerController(LawyerService lawyerService, AppointmentService appointmentService) {
        this.lawyerService = lawyerService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/api/lawyer/dashboard")
    public LawyerDashboardResponse dashboard(Authentication authentication) {
        return appointmentService.getCurrentLawyerDashboard(authentication.getName());
    }

    @PostMapping("/api/lawyer/profile")
    public ResponseEntity<LawyerProfileResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody CreateLawyerProfileRequest request) {

        LawyerProfileResponse response =
                lawyerService.createLawyerProfile(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/lawyer/profile")
    public LawyerProfileResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateLawyerProfileRequest request) {

        return lawyerService.updateCurrentLawyerProfile(authentication.getName(), request);
    }
}

