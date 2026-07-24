package com.arshraj.vakilconnect.lawyer.controller;

import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.service.LawyerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LawyerController {

    private final LawyerService lawyerService;

    public LawyerController(LawyerService lawyerService) {
        this.lawyerService = lawyerService;
    }

    @GetMapping("/api/lawyer/dashboard")
    public String dashboard() {
        return "Welcome Lawyer!";
    }

    @PostMapping("/api/lawyer/profile")
    public ResponseEntity<LawyerProfileResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody CreateLawyerProfileRequest request) {

        LawyerProfileResponse response =
                lawyerService.createLawyerProfile(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
