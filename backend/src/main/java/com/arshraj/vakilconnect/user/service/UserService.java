package com.arshraj.vakilconnect.user.service;

import com.arshraj.vakilconnect.user.dto.CurrentUserResponse;
import com.arshraj.vakilconnect.user.dto.UpdateClientProfileRequest;

public interface UserService {

    CurrentUserResponse getCurrentUser(String email);

    CurrentUserResponse updateCurrentClientProfile(String email, UpdateClientProfileRequest request);

}