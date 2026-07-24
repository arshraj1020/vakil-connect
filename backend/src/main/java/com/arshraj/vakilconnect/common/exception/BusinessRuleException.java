package com.arshraj.vakilconnect.common.exception;

/**
 * Thrown when an operation violates a business rule / invalid state transition
 * (e.g. accepting a non-pending appointment, reviewing an incomplete one).
 * Maps to HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
