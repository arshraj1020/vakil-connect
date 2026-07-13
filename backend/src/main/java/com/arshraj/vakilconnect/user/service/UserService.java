package com.arshraj.vakilconnect.user.service;

import com.arshraj.vakilconnect.user.dto.LoginRequest;
import com.arshraj.vakilconnect.user.dto.LoginResponse;
import com.arshraj.vakilconnect.user.dto.RegisterUserRequest;
import com.arshraj.vakilconnect.user.dto.UserResponse;

public interface UserService {

    UserResponse registerUser(RegisterUserRequest request);

    LoginResponse loginUser(LoginRequest request);
}