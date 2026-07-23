package com.arshraj.vakilconnect.lawyer.controller;

import com.arshraj.vakilconnect.appointment.dto.AppointmentResponse;
import com.arshraj.vakilconnect.appointment.service.AppointmentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lawyer/appointments")
public class LawyerAppointmentController {

    private final AppointmentService appointmentService;

    public LawyerAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<AppointmentResponse> schedule(Authentication authentication) {
        return appointmentService.getLawyerAppointments(authentication.getName());
    }

    @PutMapping("/{appointmentId}/accept")
    public AppointmentResponse accept(
            Authentication authentication,
            @PathVariable UUID appointmentId) {

        return appointmentService.acceptAppointment(authentication.getName(), appointmentId);
    }

    @PutMapping("/{appointmentId}/reject")
    public AppointmentResponse reject(
            Authentication authentication,
            @PathVariable UUID appointmentId) {

        return appointmentService.rejectAppointment(authentication.getName(), appointmentId);
    }

    @PutMapping("/{appointmentId}/complete")
    public AppointmentResponse complete(
            Authentication authentication,
            @PathVariable UUID appointmentId) {

        return appointmentService.completeAppointment(authentication.getName(), appointmentId);
    }
}
