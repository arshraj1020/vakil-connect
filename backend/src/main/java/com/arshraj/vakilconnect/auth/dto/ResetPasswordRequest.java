package com.arshraj.vakilconnect.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/auth/reset-password.
 *
 * The token travels in the BODY, never the URL: the email links to a frontend
 * page which then POSTs this. A mutating GET would be consumed by mail scanners
 * and link prefetchers before the user ever clicked.
 *
 * Password constraints come from PasswordRules, shared with RegisterRequest, so
 * a user can never be blocked from setting at reset a password that
 * registration would have accepted.
 */
public class ResetPasswordRequest {

    @NotBlank(message = "Token is required")
    @Size(max = 128, message = "Invalid token")
    private String token;

    @NotBlank(message = PasswordRules.REQUIRED_MESSAGE)
    @Size(min = PasswordRules.MIN_LENGTH, message = PasswordRules.LENGTH_MESSAGE)
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /** Redacted: carries both a live single-use token AND a plaintext password. */
    @Override
    public String toString() {
        return "ResetPasswordRequest{token=<redacted>, newPassword=<redacted>}";
    }
}
