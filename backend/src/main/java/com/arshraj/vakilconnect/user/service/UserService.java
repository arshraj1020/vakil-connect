package com.arshraj.vakilconnect.user.service;

import com.arshraj.vakilconnect.user.dto.RegisterUserRequest;
import com.arshraj.vakilconnect.user.entity.User;

public interface UserService {

    User registerUser(RegisterUserRequest request);

}