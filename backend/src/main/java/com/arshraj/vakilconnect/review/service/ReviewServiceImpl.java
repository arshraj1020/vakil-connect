package com.arshraj.vakilconnect.review.service;

import com.arshraj.vakilconnect.appointment.entity.Appointment;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.review.dto.CreateReviewRequest;
import com.arshraj.vakilconnect.review.dto.ReviewResponse;
import com.arshraj.vakilconnect.review.entity.Review;
import com.arshraj.vakilconnect.review.repository.ReviewRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;

    public ReviewServiceImpl(ReviewRepository reviewRepository,
                              AppointmentRepository appointmentRepository,
                              LawyerRepository lawyerRepository,
                              UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.appointmentRepository = appointmentRepository;
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public ReviewResponse createReview(String clientEmail, UUID appointmentId, CreateReviewRequest request) {
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Appointment appointment = appointmentRepository.findByIdAndClient(appointmentId, client)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("Only completed appointments can be reviewed.");
        }

        if (reviewRepository.existsByAppointmentId(appointmentId)) {
            throw new DuplicateResourceException("This appointment has already been reviewed.");
        }

        Review review = new Review();
        review.setAppointment(appointment);
        review.setClient(client);
        review.setLawyer(appointment.getLawyer());
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        Review saved = reviewRepository.save(review);

        updateLawyerRating(appointment.getLawyer(), request.getRating());

        return toResponse(saved);
    }

    @Override
    public Page<ReviewResponse> getReviewsForLawyer(UUID lawyerId, Pageable pageable) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        return reviewRepository.findByLawyerOrderByCreatedAtDesc(lawyer, pageable)
                .map(this::toResponse);
    }

    private void updateLawyerRating(Lawyer lawyer, int newRating) {
        int totalReviews = lawyer.getTotalReviews() == null ? 0 : lawyer.getTotalReviews();
        double currentRating = lawyer.getRating() == null ? 0.0 : lawyer.getRating();

        double updatedAverage =
                ((currentRating * totalReviews) + newRating) / (totalReviews + 1);

        lawyer.setRating(Math.round(updatedAverage * 100.0) / 100.0);
        lawyer.setTotalReviews(totalReviews + 1);

        lawyerRepository.save(lawyer);
    }

    private ReviewResponse toResponse(Review review) {
        ReviewResponse response = new ReviewResponse();

        response.setId(review.getId());
        response.setAppointmentId(review.getAppointment().getId());
        response.setClientName(review.getClient().getFullName());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());

        return response;
    }
}
