package com.arshraj.vakilconnect.admin.controller;

import com.arshraj.vakilconnect.admin.dto.AdminReviewResponse;
import com.arshraj.vakilconnect.admin.dto.AnalyticsResponse;
import com.arshraj.vakilconnect.admin.dto.UserSummaryResponse;
import com.arshraj.vakilconnect.admin.service.AdminService;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.user.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public AnalyticsResponse dashboard() {
        return adminService.getAnalytics();
    }

    // ---- Lawyer verification (FR-16) ----

    @GetMapping("/lawyers/pending")
    public Page<LawyerSummaryResponse> getPendingLawyers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return adminService.getPendingLawyers(PageRequest.of(page, size));
    }

    @PutMapping("/lawyers/{lawyerId}/verify")
    public LawyerProfileResponse verifyLawyer(@PathVariable UUID lawyerId) {
        return adminService.verifyLawyer(lawyerId);
    }

    // ---- User management (FR-17) ----

    @GetMapping("/users")
    public Page<UserSummaryResponse> getUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return adminService.getUsers(role, PageRequest.of(page, size));
    }

    @PutMapping("/users/{userId}/activate")
    public UserSummaryResponse activateUser(@PathVariable UUID userId) {
        return adminService.setUserActive(userId, true);
    }

    @PutMapping("/users/{userId}/deactivate")
    public UserSummaryResponse deactivateUser(@PathVariable UUID userId) {
        return adminService.setUserActive(userId, false);
    }

    // ---- Review moderation (FR-18) ----

    @GetMapping("/reviews")
    public Page<AdminReviewResponse> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return adminService.getReviews(PageRequest.of(page, size));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID reviewId) {
        adminService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

    // ---- Platform analytics (FR-19) ----

    @GetMapping("/analytics")
    public AnalyticsResponse getAnalytics() {
        return adminService.getAnalytics();
    }
}
