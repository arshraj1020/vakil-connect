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

    /**
     * The authenticated client's profile.
     *
     * Returns the same payload as GET /api/users/me, which is role-agnostic.
     * This route is kept because it is CLIENT-scoped by the security rules and
     * pairs with PUT /api/client/profile, so a client-side application can use
     * a single consistent path for reading and updating its own profile.
     */
    @GetMapping("/api/client/profile")
    public CurrentUserResponse profile(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
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
