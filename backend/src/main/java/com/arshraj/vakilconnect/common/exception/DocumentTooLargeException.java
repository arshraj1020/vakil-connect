package com.arshraj.vakilconnect.common.exception;

/**
 * The file exceeds the configured maximum. Maps to HTTP 413 with code
 * DOCUMENT_TOO_LARGE.
 *
 * THE LIMIT IS REPORTED IN THE MESSAGE, and that is a deliberate disclosure. It
 * is configuration, not a secret - the same number appears in the API docs -
 * and withholding it leaves a user bisecting file sizes to discover what will
 * be accepted.
 *
 * TWO DIFFERENT LAYERS PRODUCE THIS SAME CODE, on purpose. This exception is
 * thrown by the application's own check; Spring's container rejects anything
 * past `spring.servlet.multipart.max-file-size` BEFORE a controller runs, with
 * MaxUploadSizeExceededException. GlobalExceptionHandler maps both to 413 with
 * this code, so which layer caught it is invisible to the client - otherwise
 * the experience would change abruptly at a threshold nobody documented.
 *
 * On this project the limit is not arbitrary: document bytes live in
 * PostgreSQL, on a free-tier database with a storage cap, so the ceiling is
 * what keeps the deployment viable.
 */
public class DocumentTooLargeException extends RuntimeException {

    public static final String CODE = "DOCUMENT_TOO_LARGE";

    public DocumentTooLargeException(long maxBytes) {
        super("The file is too large. The maximum is " + (maxBytes / 1024 / 1024) + " MB.");
    }
}
