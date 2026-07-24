package com.arshraj.vakilconnect.lawyer.controller;

import com.arshraj.vakilconnect.lawyer.dto.AvailabilityResponse;
import com.arshraj.vakilconnect.lawyer.dto.CreateAvailabilityRequest;
import com.arshraj.vakilconnect.lawyer.service.AvailabilityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lawyer/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public ResponseEntity<AvailabilityResponse> add(
            Authentication authentication,
            @Valid @RequestBody CreateAvailabilityRequest request) {

        AvailabilityResponse response =
                availabilityService.addAvailability(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<AvailabilityResponse> getOwn(Authentication authentication) {
        return availabilityService.getOwnAvailability(authentication.getName());
    }

    @DeleteMapping("/{availabilityId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID availabilityId) {

        availabilityService.deleteAvailability(authentication.getName(), availabilityId);
        return ResponseEntity.noContent().build();
    }
}
