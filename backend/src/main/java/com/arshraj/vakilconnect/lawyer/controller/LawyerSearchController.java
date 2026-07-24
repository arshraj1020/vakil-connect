package com.arshraj.vakilconnect.lawyer.controller;

import com.arshraj.vakilconnect.lawyer.dto.AvailabilityResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.lawyer.service.AvailabilityService;
import com.arshraj.vakilconnect.lawyer.service.LawyerService;
import com.arshraj.vakilconnect.review.dto.ReviewResponse;
import com.arshraj.vakilconnect.review.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public endpoints for browsing lawyers. Unauthenticated clients can search,
 * filter, view profiles and read reviews before signing up.
 */
@RestController
@RequestMapping("/api/lawyers")
public class LawyerSearchController {

    private final LawyerService lawyerService;
    private final ReviewService reviewService;
    private final AvailabilityService availabilityService;

    public LawyerSearchController(LawyerService lawyerService,
                                  ReviewService reviewService,
                                  AvailabilityService availabilityService) {
        this.lawyerService = lawyerService;
        this.reviewService = reviewService;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public Page<LawyerSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minFee,
            @RequestParam(required = false) BigDecimal maxFee,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return lawyerService.searchLawyers(
                keyword, specialization, city, minFee, maxFee, minExperience, minRating, pageable
        );
    }

    @GetMapping("/{lawyerId}")
    public LawyerProfileResponse getProfile(@PathVariable UUID lawyerId) {
        return lawyerService.getLawyerProfile(lawyerId);
    }

    @GetMapping("/{lawyerId}/reviews")
    public Page<ReviewResponse> getReviews(
            @PathVariable UUID lawyerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return reviewService.getReviewsForLawyer(lawyerId, PageRequest.of(page, size));
    }

    @GetMapping("/{lawyerId}/availability")
    public List<AvailabilityResponse> getAvailability(@PathVariable UUID lawyerId) {
        return availabilityService.getAvailabilityForLawyer(lawyerId);
    }
}
