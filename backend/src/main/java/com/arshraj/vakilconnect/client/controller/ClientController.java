package com.arshraj.vakilconnect.client.controller;

import com.arshraj.vakilconnect.appointment.dto.ClientDashboardResponse;
import com.arshraj.vakilconnect.appointment.service.AppointmentService;
import com.arshraj.vakilconnect.user.dto.CurrentUserResponse;
import com.arshraj.vakilconnect.user.dto.UpdateClientProfileRequest;
import com.arshraj.vakilconnect.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientController {

    private final UserService userService;
    private final AppointmentService appointmentService;

    public ClientController(UserService userService, AppointmentService appointmentService) {
        this.userService = userService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/api/client/profile")
    public String profile() {
        return "Welcome Client!";
    }

    @PutMapping("/api/client/profile")
    public CurrentUserResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateClientProfileRequest request) {

        return userService.updateCurrentClientProfile(authentication.getName(), request);
    }

    @GetMapping("/api/client/dashboard")
    public ClientDashboardResponse dashboard(Authentication authentication) {
        return appointmentService.getCurrentClientDashboard(authentication.getName());
    }
}
