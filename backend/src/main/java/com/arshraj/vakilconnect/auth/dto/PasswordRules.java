package com.arshraj.vakilconnect.auth.dto;

/**
 * The one place a password rule is written down.
 *
 * WHY THIS EXISTS. Registration and password reset both validate a password. If
 * the two drift, a user can end up unable to set at reset a password they could
 * have set at registration - or, worse, able to set a weaker one. Both DTOs
 * reference these constants, so the rule cannot diverge without editing this
 * file.
 *
 * Deliberately constants rather than a custom annotation: jakarta.validation
 * requires annotation attributes to be compile-time constants, so a shared
 * @interface would still need these underneath, and the extra layer would buy
 * nothing.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;

    public static final String REQUIRED_MESSAGE = "Password is required";
    public static final String LENGTH_MESSAGE = "Password must be at least 8 characters";

    private PasswordRules() {
    }
}
