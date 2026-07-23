package com.arshraj.vakilconnect.review.service;

import com.arshraj.vakilconnect.review.dto.CreateReviewRequest;
import com.arshraj.vakilconnect.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {

    ReviewResponse createReview(String clientEmail, UUID appointmentId, CreateReviewRequest request);

    Page<ReviewResponse> getReviewsForLawyer(UUID lawyerId, Pageable pageable);
}
