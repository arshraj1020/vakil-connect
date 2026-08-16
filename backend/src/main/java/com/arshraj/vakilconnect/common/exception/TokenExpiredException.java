package com.arshraj.vakilconnect.common.exception;

/**
 * The token was valid but its expiry has passed. Maps to HTTP 410 Gone with
 * code TOKEN_EXPIRED.
 *
 * Kept distinct from TOKEN_INVALID on purpose. It leaks only that a token WAS
 * once valid - which whoever holds it already knows - and it buys a materially
 * better recovery path: "this link has expired, request a new one" instead of
 * an unexplained failure.
 */
public class TokenExpiredException extends RuntimeException {

    public static final String CODE = "TOKEN_EXPIRED";

    public TokenExpiredException() {
        super("This link has expired. Please request a new one.");
    }
}
