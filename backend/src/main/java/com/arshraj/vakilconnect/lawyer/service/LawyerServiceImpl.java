package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.lawyer.dto.UpdateLawyerProfileRequest;
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
import org.springframework.transaction.annotation.Transactional;

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

    /*
     * Same reasoning as verifyLawyer: this method also ends by calling
     * toProfileResponse, which touches the LAZY specializations collection.
     *
     * It had not surfaced in practice because registration creates the Lawyer
     * through AuthServiceImpl, which is annotated @Transactional at class
     * level - this endpoint (POST /api/lawyer/profile) is the other, rarer path
     * to the same mapping. Annotating it closes the latent defect and also makes
     * the several writes here (specialization resolution, then the lawyer
     * insert) atomic rather than independently committed.
     */
    @Override
    @Transactional
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

    /*
     * readOnly transactions keep the persistence context open while the
     * entities are mapped to DTOs. Lawyer.specializations is a LAZY
     * @ManyToMany and open-in-view is disabled, so without a surrounding
     * transaction the session closes when the repository call returns and
     * mapping the collection throws LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
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

    /*
     * readOnly for the same reason as the other reads: Lawyer.specializations is
     * a LAZY @ManyToMany and open-in-view is disabled, so mapping it outside a
     * transaction would throw LazyInitializationException.
     *
     * The lookup deliberately mirrors updateCurrentLawyerProfile exactly, so the
     * profile a lawyer reads is by construction the one their next PUT writes.
     */
    @Override
    @Transactional(readOnly = true)
    public LawyerProfileResponse getCurrentLawyerProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lawyer lawyer = lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));

        return toProfileResponse(lawyer);
    }

    @Override
    @Transactional(readOnly = true)
    public LawyerProfileResponse getLawyerProfile(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        return toProfileResponse(lawyer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LawyerSummaryResponse> getPendingLawyers(Pageable pageable) {
        return lawyerRepository.findByVerifiedFalse(pageable)
                .map(this::toSummaryResponse);
    }

    /*
     * @Transactional is required, not decorative.
     *
     * Without it, findById and save each ran in their own transaction and the
     * entity was detached by the time toProfileResponse mapped it. That method
     * reads `lawyer.getSpecializations()` - the only LAZY association in the
     * domain - and with open-in-view disabled the read threw
     * LazyInitializationException AFTER the save had already committed.
     *
     * The visible effect was the worst kind: the lawyer really was verified,
     * but the admin saw HTTP 500 "An unexpected error occurred" and had no way
     * to tell the write had succeeded.
     *
     * Read-write, not readOnly: this method writes.
     */
    @Override
    @Transactional
    public LawyerProfileResponse verifyLawyer(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        lawyer.setVerified(true);

        return toProfileResponse(lawyerRepository.save(lawyer));
    }

    @Override
    @Transactional
    public LawyerProfileResponse updateCurrentLawyerProfile(
            String userEmail, UpdateLawyerProfileRequest request) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lawyer lawyer = lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));

        lawyer.setExperienceYears(request.getExperienceYears());
        lawyer.setBio(request.getBio());
        lawyer.setConsultationFee(request.getConsultationFee());
        lawyer.setCity(request.getCity());
        lawyer.setOfficeAddress(request.getOfficeAddress());
        // Reuses the exact specialization resolution used at registration.
        lawyer.setSpecializations(resolveSpecializations(request.getSpecializations()));

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