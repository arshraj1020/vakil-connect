package com.arshraj.vakilconnect.client.controller;

import com.arshraj.vakilconnect.appointment.dto.AppointmentResponse;
import com.arshraj.vakilconnect.appointment.dto.BookAppointmentRequest;
import com.arshraj.vakilconnect.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(
            Authentication authentication,
            @Valid @RequestBody BookAppointmentRequest request) {

        AppointmentResponse response =
                appointmentService.bookAppointment(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<AppointmentResponse> history(Authentication authentication) {
        return appointmentService.getClientAppointments(authentication.getName());
    }

    @PutMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(
            Authentication authentication,
            @PathVariable UUID appointmentId) {

        return appointmentService.cancelAppointment(authentication.getName(), appointmentId);
    }
}
