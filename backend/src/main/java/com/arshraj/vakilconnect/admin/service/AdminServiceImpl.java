package com.arshraj.vakilconnect.admin.service;

import com.arshraj.vakilconnect.admin.dto.AdminReviewResponse;
import com.arshraj.vakilconnect.admin.dto.AnalyticsResponse;
import com.arshraj.vakilconnect.admin.dto.UserSummaryResponse;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.lawyer.service.LawyerService;
import com.arshraj.vakilconnect.review.entity.Review;
import com.arshraj.vakilconnect.review.repository.ReviewRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminServiceImpl implements AdminService {

    private final LawyerService lawyerService;
    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;

    public AdminServiceImpl(LawyerService lawyerService,
                             LawyerRepository lawyerRepository,
                             UserRepository userRepository,
                             ReviewRepository reviewRepository,
                             AppointmentRepository appointmentRepository) {
        this.lawyerService = lawyerService;
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public Page<LawyerSummaryResponse> getPendingLawyers(Pageable pageable) {
        return lawyerService.getPendingLawyers(pageable);
    }

    @Override
    public LawyerProfileResponse verifyLawyer(UUID lawyerId) {
        return lawyerService.verifyLawyer(lawyerId);
    }

    @Override
    public Page<UserSummaryResponse> getUsers(Role role, Pageable pageable) {
        Page<User> users = (role == null)
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);

        return users.map(this::toUserSummary);
    }

    @Override
    @Transactional
    public UserSummaryResponse setUserActive(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setActive(active);

        return toUserSummary(userRepository.save(user));
    }

    @Override
    public Page<AdminReviewResponse> getReviews(Pageable pageable) {
        return reviewRepository.findAll(pageable).map(this::toAdminReviewResponse);
    }

    @Override
    @Transactional
    public void deleteReview(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        Lawyer lawyer = review.getLawyer();

        int totalReviews = lawyer.getTotalReviews() == null ? 0 : lawyer.getTotalReviews();
        double currentRating = lawyer.getRating() == null ? 0.0 : lawyer.getRating();
        int newTotal = Math.max(totalReviews - 1, 0);

        if (newTotal == 0) {
            lawyer.setRating(0.0);
            lawyer.setTotalReviews(0);
        } else {
            double newAverage = ((currentRating * totalReviews) - review.getRating()) / newTotal;
            lawyer.setRating(Math.round(newAverage * 100.0) / 100.0);
            lawyer.setTotalReviews(newTotal);
        }

        lawyerRepository.save(lawyer);
        reviewRepository.delete(review);
    }

    @Override
    public AnalyticsResponse getAnalytics() {
        AnalyticsResponse response = new AnalyticsResponse();

        response.setTotalUsers(userRepository.count());
        response.setTotalClients(userRepository.countByRole(Role.CLIENT));
        response.setTotalLawyers(userRepository.countByRole(Role.LAWYER));
        response.setTotalAdmins(userRepository.countByRole(Role.ADMIN));

        response.setVerifiedLawyers(lawyerRepository.countByVerifiedTrue());
        response.setUnverifiedLawyers(lawyerRepository.countByVerifiedFalse());

        response.setTotalAppointments(appointmentRepository.count());
        response.setPendingAppointments(appointmentRepository.countByStatus(AppointmentStatus.PENDING));
        response.setAcceptedAppointments(appointmentRepository.countByStatus(AppointmentStatus.ACCEPTED));
        response.setCompletedAppointments(appointmentRepository.countByStatus(AppointmentStatus.COMPLETED));
        response.setRejectedAppointments(appointmentRepository.countByStatus(AppointmentStatus.REJECTED));
        response.setCancelledAppointments(appointmentRepository.countByStatus(AppointmentStatus.CANCELLED));

        long totalReviews = reviewRepository.count();
        response.setTotalReviews(totalReviews);

        double averageRating = lawyerRepository.findAll().stream()
                .filter(l -> l.getTotalReviews() != null && l.getTotalReviews() > 0)
                .mapToDouble(Lawyer::getRating)
                .average()
                .orElse(0.0);

        response.setAveragePlatformRating(Math.round(averageRating * 100.0) / 100.0);

        return response;
    }

    private UserSummaryResponse toUserSummary(User user) {
        UserSummaryResponse response = new UserSummaryResponse();

        response.setId(user.getId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole().name());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());

        return response;
    }

    private AdminReviewResponse toAdminReviewResponse(Review review) {
        AdminReviewResponse response = new AdminReviewResponse();

        response.setId(review.getId());
        response.setAppointmentId(review.getAppointment().getId());
        response.setClientName(review.getClient().getFullName());
        response.setLawyerName(review.getLawyer().getUser().getFullName());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());

        return response;
    }
}
