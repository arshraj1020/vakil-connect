package com.arshraj.vakilconnect.appointment.service;

import com.arshraj.vakilconnect.appointment.dto.AppointmentResponse;
import com.arshraj.vakilconnect.appointment.dto.BookAppointmentRequest;
import com.arshraj.vakilconnect.appointment.entity.Appointment;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.AvailabilityRepository;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final LawyerRepository lawyerRepository;
    private final AvailabilityRepository availabilityRepository;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                   UserRepository userRepository,
                                   LawyerRepository lawyerRepository,
                                   AvailabilityRepository availabilityRepository) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.lawyerRepository = lawyerRepository;
        this.availabilityRepository = availabilityRepository;
    }

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(String clientEmail, BookAppointmentRequest request) {
        User client = getUser(clientEmail);

        Lawyer lawyer = lawyerRepository.findById(request.getLawyerId())
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        if (!Boolean.TRUE.equals(lawyer.getVerified())) {
            throw new BusinessRuleException("This lawyer is not yet verified and cannot accept appointments.");
        }

        // The requested time must fall within one of the lawyer's available windows.
        DayOfWeek day = request.getAppointmentDate().getDayOfWeek();
        LocalTime time = request.getAppointmentTime();

        boolean withinAvailability = availabilityRepository
                .findByLawyerAndDayOfWeekAndAvailableTrue(lawyer, day)
                .stream()
                .anyMatch(slot ->
                        !time.isBefore(slot.getStartTime()) && time.isBefore(slot.getEndTime()));

        if (!withinAvailability) {
            throw new BusinessRuleException(
                    "The lawyer is not available at the requested day and time.");
        }

        // Prevent double-booking the same slot.
        boolean slotTaken = appointmentRepository
                .existsByLawyerAndAppointmentDateAndAppointmentTimeAndStatusIn(
                        lawyer,
                        request.getAppointmentDate(),
                        time,
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.ACCEPTED));

        if (slotTaken) {
            throw new BusinessRuleException("This time slot is already booked.");
        }

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setLawyer(lawyer);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setAppointmentTime(request.getAppointmentTime());
        appointment.setConsultationMode(request.getConsultationMode());
        appointment.setNotes(request.getNotes());
        appointment.setStatus(AppointmentStatus.PENDING);

        Appointment saved = appointmentRepository.save(appointment);

        return toResponse(saved);
    }

    @Override
    public List<AppointmentResponse> getClientAppointments(String clientEmail) {
        User client = getUser(clientEmail);

        return appointmentRepository
                .findByClientOrderByAppointmentDateDescAppointmentTimeDesc(client)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(String clientEmail, UUID appointmentId) {
        User client = getUser(clientEmail);

        Appointment appointment = appointmentRepository.findByIdAndClient(appointmentId, client)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.REJECTED) {
            throw new BusinessRuleException("This appointment can no longer be cancelled.");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);

        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    public List<AppointmentResponse> getLawyerAppointments(String lawyerEmail) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);

        return appointmentRepository
                .findByLawyerOrderByAppointmentDateDescAppointmentTimeDesc(lawyer)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppointmentResponse acceptAppointment(String lawyerEmail, UUID appointmentId) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);
        Appointment appointment = getOwnAppointment(appointmentId, lawyer);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessRuleException("Only pending appointments can be accepted.");
        }

        appointment.setStatus(AppointmentStatus.ACCEPTED);

        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponse rejectAppointment(String lawyerEmail, UUID appointmentId) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);
        Appointment appointment = getOwnAppointment(appointmentId, lawyer);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessRuleException("Only pending appointments can be rejected.");
        }

        appointment.setStatus(AppointmentStatus.REJECTED);

        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponse completeAppointment(String lawyerEmail, UUID appointmentId) {
        Lawyer lawyer = getLawyerByEmail(lawyerEmail);
        Appointment appointment = getOwnAppointment(appointmentId, lawyer);

        if (appointment.getStatus() != AppointmentStatus.ACCEPTED) {
            throw new BusinessRuleException("Only accepted appointments can be marked as completed.");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);

        return toResponse(appointmentRepository.save(appointment));
    }

    private Appointment getOwnAppointment(UUID appointmentId, Lawyer lawyer) {
        return appointmentRepository.findByIdAndLawyer(appointmentId, lawyer)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Lawyer getLawyerByEmail(String email) {
        User user = getUser(email);
        return lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        AppointmentResponse response = new AppointmentResponse();

        response.setId(appointment.getId());

        response.setLawyerId(appointment.getLawyer().getId());
        response.setLawyerName(appointment.getLawyer().getUser().getFullName());

        response.setClientId(appointment.getClient().getId());
        response.setClientName(appointment.getClient().getFullName());

        response.setAppointmentDate(appointment.getAppointmentDate());
        response.setAppointmentTime(appointment.getAppointmentTime());
        response.setConsultationMode(appointment.getConsultationMode().name());
        response.setStatus(appointment.getStatus().name());
        response.setNotes(appointment.getNotes());
        response.setCreatedAt(appointment.getCreatedAt());

        return response;
    }
}
