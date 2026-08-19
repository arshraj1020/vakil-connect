package com.arshraj.vakilconnect.common.exception;

/**
 * A document's text could not be read. Maps to HTTP 422 with code
 * DOCUMENT_EXTRACTION_FAILED.
 *
 * 422 UNPROCESSABLE CONTENT, not 400 and not 500. The request was well formed
 * and the server understood it; the stored FILE is what cannot be processed.
 * A 400 would blame the request, and a 500 would blame the server for a
 * password-protected PDF the user chose to upload.
 *
 * COVERS THREE SITUATIONS, deliberately behind one code: a corrupt file, an
 * encrypted one, and a scanned image with no text layer. The frontend does the
 * same thing for all three - tell the user this file cannot be indexed - and
 * separating them would mostly be guesswork, since Tika does not reliably
 * distinguish "encrypted" from "malformed".
 *
 * THE PARSER'S OWN MESSAGE NEVER REACHES THIS ONE. Tika, PDFBox and POI embed
 * byte offsets, object numbers and sometimes fragments of document content in
 * their exception messages. The cause is attached so a stack trace is still
 * debuggable, but the message here is fixed and never interpolates it - because
 * this message is returned to the client and written to logs.
 */
public class DocumentExtractionException extends RuntimeException {

    public static final String CODE = "DOCUMENT_EXTRACTION_FAILED";

    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
