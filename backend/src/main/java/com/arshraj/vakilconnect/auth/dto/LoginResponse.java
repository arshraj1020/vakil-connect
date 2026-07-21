package com.arshraj.vakilconnect.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType;
    private String fullName;
    private String email;
    private String role;
}