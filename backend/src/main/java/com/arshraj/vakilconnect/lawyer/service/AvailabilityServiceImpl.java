package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.dto.AvailabilityResponse;
import com.arshraj.vakilconnect.lawyer.dto.CreateAvailabilityRequest;
import com.arshraj.vakilconnect.lawyer.entity.Availability;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.AvailabilityRepository;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;

    public AvailabilityServiceImpl(AvailabilityRepository availabilityRepository,
                                    LawyerRepository lawyerRepository,
                                    UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public AvailabilityResponse addAvailability(String lawyerEmail, CreateAvailabilityRequest request) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);

        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessRuleException("Start time must be before end time.");
        }

        boolean duplicate = availabilityRepository.existsByLawyerAndDayOfWeekAndStartTimeAndEndTime(
                lawyer, request.getDayOfWeek(), request.getStartTime(), request.getEndTime());
        if (duplicate) {
            throw new DuplicateResourceException("This availability slot already exists.");
        }

        Availability availability = new Availability();
        availability.setLawyer(lawyer);
        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setStartTime(request.getStartTime());
        availability.setEndTime(request.getEndTime());
        availability.setAvailable(true);

        return toResponse(availabilityRepository.save(availability));
    }

    @Override
    public List<AvailabilityResponse> getOwnAvailability(String lawyerEmail) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);
        return mapList(lawyer);
    }

    @Override
    @Transactional
    public void deleteAvailability(String lawyerEmail, UUID availabilityId) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);

        Availability availability = availabilityRepository.findByIdAndLawyer(availabilityId, lawyer)
                .orElseThrow(() -> new ResourceNotFoundException("Availability slot not found"));

        availabilityRepository.delete(availability);
    }

    @Override
    public List<AvailabilityResponse> getAvailabilityForLawyer(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));
        return mapList(lawyer);
    }

    private List<AvailabilityResponse> mapList(Lawyer lawyer) {
        return availabilityRepository
                .findByLawyerOrderByDayOfWeekAscStartTimeAsc(lawyer)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Lawyer getLawyerByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));
    }

    private AvailabilityResponse toResponse(Availability availability) {
        AvailabilityResponse response = new AvailabilityResponse();
        response.setId(availability.getId());
        response.setDayOfWeek(availability.getDayOfWeek());
        response.setStartTime(availability.getStartTime());
        response.setEndTime(availability.getEndTime());
        response.setAvailable(availability.isAvailable());
        return response;
    }
}
