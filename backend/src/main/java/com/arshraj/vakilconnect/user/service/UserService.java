package com.arshraj.vakilconnect.user.service;

import com.arshraj.vakilconnect.user.dto.RegisterUserRequest;
import com.arshraj.vakilconnect.user.dto.UserResponse;
import com.arshraj.vakilconnect.user.entity.User;

public interface UserService {

    UserResponse registerUser(RegisterUserRequest request);

}