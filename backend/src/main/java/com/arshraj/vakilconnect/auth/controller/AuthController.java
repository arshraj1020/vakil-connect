package com.arshraj.vakilconnect.auth.controller;

import com.arshraj.vakilconnect.auth.dto.AcknowledgementResponse;
import com.arshraj.vakilconnect.auth.dto.ForgotPasswordRequest;
import com.arshraj.vakilconnect.auth.dto.LoginRequest;
import com.arshraj.vakilconnect.auth.dto.LoginResponse;
import com.arshraj.vakilconnect.auth.dto.PasswordResetResponse;
import com.arshraj.vakilconnect.auth.dto.RegisterRequest;
import com.arshraj.vakilconnect.auth.dto.RegisterResponse;
import com.arshraj.vakilconnect.auth.dto.ResendVerificationRequest;
import com.arshraj.vakilconnect.auth.dto.ResetPasswordRequest;
import com.arshraj.vakilconnect.auth.dto.VerificationResponse;
import com.arshraj.vakilconnect.auth.dto.VerifyEmailRequest;
import com.arshraj.vakilconnect.auth.service.AuthService;
import com.arshraj.vakilconnect.identity.service.EmailVerificationService;
import com.arshraj.vakilconnect.identity.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    /* ------------------------------------------------ email verification (P4) --
     *
     * Both endpoints are PUBLIC by necessity: a user who cannot log in yet must
     * still be able to verify, and one who never received the email must be
     * able to ask again. Neither reads the SecurityContext.
     */

    /**
     * POST, never GET.
     *
     * The email links to a frontend page which then calls this. A mutating GET
     * would be fetched by mail scanners and link prefetchers before the human
     * clicked, consuming the single-use token and leaving the user with an
     * "invalid link".
     *
     * Failures are typed exceptions from the Phase 2 token core, already mapped
     * by GlobalExceptionHandler: 400 TOKEN_INVALID, 410 TOKEN_EXPIRED,
     * 409 TOKEN_ALREADY_USED.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<VerificationResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        emailVerificationService.verify(request.getToken());

        return ResponseEntity.ok(new VerificationResponse(
                true, "Your email has been verified. You can now sign in."));
    }

    /**
     * 202 ACCEPTED, ALWAYS, WITH AN IDENTICAL BODY.
     *
     * Unknown address, already-verified account and deactivated account all
     * produce this exact response. The service returns void precisely so that
     * no branch here can accidentally report which case occurred - that would
     * turn this into an account-enumeration oracle that also sends mail.
     *
     * 202 rather than 200 is honest: the email has been queued for dispatch
     * after commit, not delivered.
     *
     * The one exception is CooldownActiveException (429), which is reachable
     * only for an existing unverified account and is therefore a deliberate,
     * documented narrow leak.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<AcknowledgementResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        emailVerificationService.resend(request.getEmail());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new AcknowledgementResponse(
                        "If that address needs verification, we have sent a new link."));
    }

    /* ---------------------------------------------------- password reset (P6) --
     *
     * Both endpoints are PUBLIC by necessity: someone who has forgotten their
     * password cannot authenticate to ask for a new one. Neither reads the
     * SecurityContext; authority comes from possession of a single-use,
     * expiring token delivered to the account's mailbox.
     */

    /**
     * 202 ACCEPTED, ALWAYS, WITH AN IDENTICAL BODY.
     *
     * Unknown address and deactivated account produce this exact response, the
     * same as a successful request. The service returns void precisely so that
     * no branch here can accidentally report which case occurred - that would
     * turn this into an account-enumeration oracle that also sends mail.
     *
     * 202 rather than 200 is honest: the email is queued for dispatch after
     * commit, not delivered.
     *
     * The one exception is CooldownActiveException (429), reachable only for an
     * existing active account - the same deliberate, documented narrow leak
     * already accepted for resend-verification.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<AcknowledgementResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestReset(request.getEmail());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new AcknowledgementResponse(
                        "If an account exists for that address, we have sent a reset link."));
    }

    /**
     * Applies a new password and ends every existing session.
     *
     * Returns NO JWT (D9). The user signs in with the password they just chose,
     * which also confirms they remember it.
     *
     * Token failures surface as the Phase 2 typed exceptions, already mapped:
     * 400 TOKEN_INVALID, 410 TOKEN_EXPIRED, 409 TOKEN_ALREADY_USED.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());

        return ResponseEntity.ok(new PasswordResetResponse(
                true, "Your password has been reset. Please sign in with your new password."));
    }
}