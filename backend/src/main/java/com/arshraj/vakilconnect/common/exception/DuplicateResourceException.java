package com.arshraj.vakilconnect.common.exception;

/**
 * Thrown when creating an entity that violates a uniqueness rule
 * (duplicate email, bar council number, review, etc.). Maps to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
