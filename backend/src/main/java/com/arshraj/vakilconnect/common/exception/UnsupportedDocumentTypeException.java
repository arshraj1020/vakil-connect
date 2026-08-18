package com.arshraj.vakilconnect.common.exception;

/**
 * The file is not a PDF, DOCX or TXT. Maps to HTTP 415 with code
 * DOCUMENT_TYPE_UNSUPPORTED.
 *
 * 415 UNSUPPORTED MEDIA TYPE rather than 400: the request was well formed and
 * the server understood it perfectly - it declines the PAYLOAD FORMAT, which is
 * exactly what 415 exists to say. A 400 would suggest the client built the
 * request wrongly and send them looking in the wrong place.
 *
 * COVERS TWO SITUATIONS DELIBERATELY COLLAPSED INTO ONE CODE:
 *
 *   * an extension outside the allowed set (`.exe`, `.zip`, `.pages`);
 *   * an allowed extension whose BYTES say otherwise - an executable renamed to
 *     .pdf, or a real PDF saved as .txt.
 *
 * The second is the security-relevant one, and separating it into its own code
 * would tell an attacker precisely which check caught them. The frontend needs
 * the same message either way: this file cannot be uploaded.
 *
 * The message lists what IS accepted, which is the only actionable half. It
 * never reports what the file was detected as - that is a probing oracle.
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public static final String CODE = "DOCUMENT_TYPE_UNSUPPORTED";

    public UnsupportedDocumentTypeException() {
        super("Only PDF, DOCX and TXT files can be uploaded.");
    }
}
