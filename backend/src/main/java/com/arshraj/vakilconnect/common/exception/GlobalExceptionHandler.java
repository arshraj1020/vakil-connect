package com.arshraj.vakilconnect.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates exceptions into consistent HTTP status codes and a structured
 * {@link ErrorResponse} body, so business failures no longer surface as 500s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI());
        body.setFieldErrors(fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request);
    }

    /**
     * A path variable or request parameter that cannot be converted to the
     * declared type (e.g. a non-UUID appointment id) is a client error.
     *
     * This handler is required because MethodArgumentTypeMismatchException is a
     * RuntimeException: without it the generic fallback below would catch it and
     * return 500. The parameter name is reported but its raw value is not echoed
     * back to the caller.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /* ------------------------------------------------- identity tokens (P2) --
     *
     * Three distinct codes rather than one generic failure, because the
     * frontend has to do three different things: retry, offer a fresh link, or
     * treat the attempt as already-succeeded. Each message is fixed at the
     * exception, never assembled from caller input.
     */

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleTokenInvalid(
            TokenInvalidException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request,
                TokenInvalidException.CODE);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(
            TokenExpiredException ex, HttpServletRequest request) {
        // 410 Gone: the resource existed and no longer does. More useful to a
        // client than a bare 400, and it costs no secrecy the holder lacks.
        return build(HttpStatus.GONE, ex.getMessage(), request,
                TokenExpiredException.CODE);
    }

    @ExceptionHandler(TokenAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleTokenAlreadyUsed(
            TokenAlreadyUsedException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request,
                TokenAlreadyUsedException.CODE);
    }

    /**
     * A verification email was requested again too soon.
     *
     * 429 with `Retry-After` in seconds, so a client can render a countdown
     * instead of guessing. This is the ORDINARY cooldown, detected by reading
     * the newest token before anything is written - distinct from the
     * concurrent-race outcome below, which is a 409.
     */
    @ExceptionHandler(CooldownActiveException.class)
    public ResponseEntity<ErrorResponse> handleCooldownActive(
            CooldownActiveException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
        body.setCode(CooldownActiveException.CODE);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(body);
    }

    /**
     * A database constraint rejected the write.
     *
     * THIS IS THE K1 DECISION. A concurrent second token issue is stopped by the
     * partial unique index `uq_email_tokens_live`, and the resulting violation
     * is NOT caught in the service: catching it there would leave the
     * transaction aborted, so every following statement would fail with
     * "current transaction is aborted". Letting it propagate to this handler
     * keeps the database as the final authority on concurrent inserts and keeps
     * the service free of transaction-state bookkeeping.
     *
     * 409, not 500: the request conflicted with existing state, which is the
     * caller's situation rather than a server fault. Note this also upgrades any
     * previously-uncaught violation elsewhere in the application from a 500 to a
     * 409 - a strictly more accurate answer.
     *
     * Logged at WARN with the URI but WITHOUT the exception message in the
     * response: constraint and column names are internal schema detail.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Data integrity violation at {}", request.getRequestURI(), ex);

        return build(HttpStatus.CONFLICT,
                "This request conflicts with existing data.", request,
                "RESOURCE_CONFLICT");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            RuntimeException ex, HttpServletRequest request) {

        /*
         * Log it. This handler CONSUMES the exception, so Spring never logs it
         * either - without this line every unexpected production failure would
         * be invisible: no stack trace, no class name, anywhere.
         */
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);

        // Do not leak internal details to the client.
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, null);
    }

    /**
     * A null {@code code} is omitted from the JSON entirely (see
     * ErrorResponse), so every handler that does not supply one produces a body
     * byte-identical to the one it produced before this field existed.
     */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request, String code) {

        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());

        body.setCode(code);

        return ResponseEntity.status(status).body(body);
    }
}
