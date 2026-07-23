package com.arshraj.vakilconnect.user.service;

import com.arshraj.vakilconnect.user.dto.CurrentUserResponse;

public interface UserService {

    CurrentUserResponse getCurrentUser(String email);

}