package com.arshraj.vakilconnect.user.controller;

import com.arshraj.vakilconnect.user.dto.RegisterUserRequest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.arshraj.vakilconnect.user.dto.UserResponse;

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

}