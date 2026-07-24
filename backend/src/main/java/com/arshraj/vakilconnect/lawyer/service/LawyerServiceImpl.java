package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.entity.Specialization;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.lawyer.repository.SpecializationRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LawyerServiceImpl implements LawyerService {

    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;
    private final SpecializationRepository specializationRepository;

    public LawyerServiceImpl(LawyerRepository lawyerRepository,
                             UserRepository userRepository,
                             SpecializationRepository specializationRepository) {
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
        this.specializationRepository = specializationRepository;
    }

    @Override
    public LawyerProfileResponse createLawyerProfile(
            String userEmail,
            CreateLawyerProfileRequest request) {

        // Find logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != Role.LAWYER) {
            throw new BusinessRuleException("Only users registered as LAWYER can create a lawyer profile.");
        }

        // Prevent duplicate lawyer profile
        if (lawyerRepository.existsByUser(user)) {
            throw new DuplicateResourceException("Lawyer profile already exists.");
        }

        // Prevent duplicate Bar Council Number
        if (lawyerRepository.existsByBarCouncilNumber(request.getBarCouncilNumber())) {
            throw new DuplicateResourceException("Bar Council Number already registered.");
        }

        // Create Lawyer entity
        Lawyer lawyer = new Lawyer();

        lawyer.setUser(user);
        lawyer.setBarCouncilNumber(request.getBarCouncilNumber());
        lawyer.setExperienceYears(request.getExperienceYears());
        lawyer.setBio(request.getBio());
        lawyer.setConsultationFee(request.getConsultationFee());
        lawyer.setCity(request.getCity());
        lawyer.setOfficeAddress(request.getOfficeAddress());
        lawyer.setSpecializations(resolveSpecializations(request.getSpecializations()));

        // Save Lawyer
        Lawyer savedLawyer = lawyerRepository.save(lawyer);

        return toProfileResponse(savedLawyer);
    }

    @Override
    public Page<LawyerSummaryResponse> searchLawyers(
            String keyword,
            String specialization,
            String city,
            BigDecimal minFee,
            BigDecimal maxFee,
            Integer minExperience,
            Double minRating,
            Pageable pageable) {

        Page<Lawyer> lawyers = lawyerRepository.search(
                blankToNull(keyword),
                blankToNull(specialization),
                blankToNull(city),
                minFee,
                maxFee,
                minExperience,
                minRating,
                pageable
        );

        return lawyers.map(this::toSummaryResponse);
    }

    @Override
    public LawyerProfileResponse getLawyerProfile(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        return toProfileResponse(lawyer);
    }

    @Override
    public Page<LawyerSummaryResponse> getPendingLawyers(Pageable pageable) {
        return lawyerRepository.findByVerifiedFalse(pageable)
                .map(this::toSummaryResponse);
    }

    @Override
    public LawyerProfileResponse verifyLawyer(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        lawyer.setVerified(true);

        return toProfileResponse(lawyerRepository.save(lawyer));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private LawyerProfileResponse toProfileResponse(Lawyer lawyer) {
        LawyerProfileResponse response = new LawyerProfileResponse();

        response.setId(lawyer.getId());
        response.setFullName(lawyer.getUser().getFullName());
        response.setEmail(lawyer.getUser().getEmail());
        response.setPhoneNumber(lawyer.getUser().getPhoneNumber());

        response.setBarCouncilNumber(lawyer.getBarCouncilNumber());
        response.setExperienceYears(lawyer.getExperienceYears());
        response.setBio(lawyer.getBio());
        response.setConsultationFee(lawyer.getConsultationFee());
        response.setCity(lawyer.getCity());
        response.setOfficeAddress(lawyer.getOfficeAddress());

        response.setVerified(lawyer.getVerified());
        response.setRating(lawyer.getRating());
        response.setTotalReviews(lawyer.getTotalReviews());
        response.setSpecializations(
                lawyer.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList())
        );

        return response;
    }

    private LawyerSummaryResponse toSummaryResponse(Lawyer lawyer) {
        LawyerSummaryResponse response = new LawyerSummaryResponse();

        response.setId(lawyer.getId());
        response.setFullName(lawyer.getUser().getFullName());
        response.setCity(lawyer.getCity());
        response.setExperienceYears(lawyer.getExperienceYears());
        response.setConsultationFee(lawyer.getConsultationFee());
        response.setRating(lawyer.getRating());
        response.setTotalReviews(lawyer.getTotalReviews());
        response.setSpecializations(
                lawyer.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList())
        );

        return response;
    }

    private Set<Specialization> resolveSpecializations(List<String> names) {
        Set<Specialization> specializations = new LinkedHashSet<>();

        for (String rawName : names) {
            String name = rawName.trim();
            if (name.isEmpty()) {
                continue;
            }

            Specialization specialization = specializationRepository
                    .findByNameIgnoreCase(name)
                    .orElseGet(() -> specializationRepository.save(new Specialization(name)));

            specializations.add(specialization);
        }

        return specializations;
    }
}