package com.arshraj.vakilconnect.common.exception;

/**
 * The filename is missing, or nothing usable survived sanitising. Maps to HTTP
 * 400 with code DOCUMENT_NAME_INVALID.
 *
 * Reached when the name is absent, blank, or consists ENTIRELY of parts the
 * sanitiser removes - "../..", a string of control characters, only dots. An
 * ordinary name containing one hostile character is cleaned and accepted, not
 * refused: rejecting an upload the user believes is fine, over a character they
 * cannot see, would be hostile.
 *
 * THE REJECTED VALUE IS NEVER ECHOED. The message is fixed. Reflecting an
 * attacker-supplied filename into a response body is how a stored-XSS payload
 * reaches a page that renders errors, and the caller already knows what they
 * sent.
 */
public class InvalidDocumentNameException extends RuntimeException {

    public static final String CODE = "DOCUMENT_NAME_INVALID";

    public InvalidDocumentNameException() {
        super("The file name is missing or not usable.");
    }
}
