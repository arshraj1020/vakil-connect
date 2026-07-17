package com.arshraj.vakilconnect.auth.service;

import com.arshraj.vakilconnect.auth.dto.RegisterRequest;
import com.arshraj.vakilconnect.auth.dto.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

}