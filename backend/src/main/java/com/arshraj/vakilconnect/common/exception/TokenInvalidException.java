package com.arshraj.vakilconnect.common.exception;

/**
 * The presented token does not exist, is of the wrong type for the endpoint, or
 * was superseded by a newer one. Maps to HTTP 400 with code TOKEN_INVALID.
 *
 * Deliberately collapses "never existed" and "superseded" into one response: the
 * holder of a token learns nothing from the distinction, and separating them
 * would confirm to an attacker that a guessed value once existed.
 *
 * The message is fixed rather than caller-supplied for the same reason.
 */
public class TokenInvalidException extends RuntimeException {

    public static final String CODE = "TOKEN_INVALID";

    public TokenInvalidException() {
        super("This link is invalid.");
    }
}
