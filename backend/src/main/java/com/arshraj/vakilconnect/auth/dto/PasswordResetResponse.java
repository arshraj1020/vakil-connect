package com.arshraj.vakilconnect.auth.dto;

/**
 * Response of a successful POST /api/auth/reset-password.
 *
 * Carries NO JWT, deliberately. Auto-login after a flow whose entire premise is
 * "we are not certain who you are" is the wrong instinct - the user signs in
 * with the password they just chose, which also proves they remember it.
 */
public class PasswordResetResponse {

    private boolean reset;
    private String message;

    public PasswordResetResponse() {
    }

    public PasswordResetResponse(boolean reset, String message) {
        this.reset = reset;
        this.message = message;
    }

    public boolean isReset() {
        return reset;
    }

    public void setReset(boolean reset) {
        this.reset = reset;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
