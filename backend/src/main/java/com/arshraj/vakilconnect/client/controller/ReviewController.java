package com.arshraj.vakilconnect.client.controller;

import com.arshraj.vakilconnect.review.dto.CreateReviewRequest;
import com.arshraj.vakilconnect.review.dto.ReviewResponse;
import com.arshraj.vakilconnect.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/client/appointments/{appointmentId}/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            Authentication authentication,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CreateReviewRequest request) {

        ReviewResponse response =
                reviewService.createReview(authentication.getName(), appointmentId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
