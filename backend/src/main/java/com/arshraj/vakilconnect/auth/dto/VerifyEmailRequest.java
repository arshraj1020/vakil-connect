package com.arshraj.vakilconnect.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/auth/verify-email.
 *
 * The token travels in the BODY, never in the URL. The email links to a
 * frontend page which then POSTs this - a mutating GET would be consumed by
 * mail scanners and link prefetchers before the user ever clicked.
 *
 * @Size caps an otherwise unbounded input: a legitimate token is 43 characters,
 * so the bound stops an oversized payload reaching the HMAC.
 */
public class VerifyEmailRequest {

    @NotBlank(message = "Token is required")
    @Size(max = 128, message = "Invalid token")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** Redacted: the token is a live single-use credential. */
    @Override
    public String toString() {
        return "VerifyEmailRequest{token=<redacted>}";
    }
}
