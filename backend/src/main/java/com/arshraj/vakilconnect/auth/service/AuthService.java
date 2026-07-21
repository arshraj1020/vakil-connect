package com.arshraj.vakilconnect.auth.service;

import com.arshraj.vakilconnect.auth.dto.LoginRequest;
import com.arshraj.vakilconnect.auth.dto.LoginResponse;
import com.arshraj.vakilconnect.auth.dto.RegisterRequest;
import com.arshraj.vakilconnect.auth.dto.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}