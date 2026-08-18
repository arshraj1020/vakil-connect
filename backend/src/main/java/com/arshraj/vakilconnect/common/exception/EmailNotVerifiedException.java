package com.arshraj.vakilconnect.common.exception;

/**
 * Login was refused because the account's email address has not been verified.
 * Maps to HTTP 403 with code EMAIL_NOT_VERIFIED.
 *
 * DISTINCT FROM 401 ON PURPOSE. The credentials were correct - answering 401
 * would tell the user their password was wrong and send them to reset a
 * password that works perfectly well. 403 says "we know who you are, you may
 * not proceed yet", which is exactly the situation.
 *
 * DISTINCT FROM ACCOUNT DEACTIVATION. An admin-disabled account fails earlier,
 * inside DaoAuthenticationProvider, and surfaces as a DisabledException. Keeping
 * the two separate is what lets the frontend show a resend-verification button
 * here and a contact-support message there.
 *
 * The message is fixed rather than caller-supplied, and reveals nothing beyond
 * what the authenticated caller already knows about their own account.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException() {
        super("Please verify your email address before signing in.");
    }
}
