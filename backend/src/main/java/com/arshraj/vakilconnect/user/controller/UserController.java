package com.arshraj.vakilconnect.user.controller;

import com.arshraj.vakilconnect.user.dto.CurrentUserResponse;
import com.arshraj.vakilconnect.user.dto.LoginRequest;
import com.arshraj.vakilconnect.user.dto.LoginResponse;
import com.arshraj.vakilconnect.user.dto.RegisterUserRequest;
import com.arshraj.vakilconnect.user.dto.UserResponse;
import com.arshraj.vakilconnect.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public UserResponse registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return userService.registerUser(request);
    }

    @PostMapping("/login")
    public LoginResponse loginUser(@Valid @RequestBody LoginRequest request) {
        return userService.loginUser(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
    }
}