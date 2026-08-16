package com.arshraj.vakilconnect.common.exception;

/**
 * The token was already consumed. Maps to HTTP 409 with code
 * TOKEN_ALREADY_USED.
 *
 * This is the ordinary outcome of a double click, a mail client prefetching the
 * link, or the user opening it twice - not an attack. The distinct code exists
 * so the frontend can render the second attempt as SUCCESS when the underlying
 * action already completed: the user's question is "did it work", not "was mine
 * the first request".
 */
public class TokenAlreadyUsedException extends RuntimeException {

    public static final String CODE = "TOKEN_ALREADY_USED";

    public TokenAlreadyUsedException() {
        super("This link has already been used.");
    }
}
