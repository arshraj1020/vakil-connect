package com.arshraj.vakilconnect.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error body returned for every handled exception.
 */
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;

    /**
     * Machine-readable failure code, so the frontend can branch on an identifier
     * instead of pattern-matching an English sentence that copy edits will break.
     *
     * NON_NULL IS LOAD-BEARING, NOT TIDINESS. Jackson serialises nulls by
     * default, so without this annotation every error body already in production
     * would gain `"code": null` - a contract change for handlers that were not
     * touched. The annotation is on THIS FIELD ONLY and deliberately not on the
     * class: at class level it would also drop the existing `"fieldErrors": null`
     * that current responses carry, which is the same breakage in the other
     * direction.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;

    public ErrorResponse() {
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
