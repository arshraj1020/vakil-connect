package com.arshraj.vakilconnect.common.exception;

/**
 * Thrown when a requested entity does not exist. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
