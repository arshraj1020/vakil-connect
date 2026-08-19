package com.arshraj.vakilconnect.common.exception;

/**
 * Embeddings could not be generated. Maps to HTTP 503 with code
 * EMBEDDING_UNAVAILABLE.
 *
 * 503 SERVICE UNAVAILABLE, and the choice is deliberate: the overwhelmingly
 * common cause is that Ollama is not running or the model was never pulled.
 * That is a DEPENDENCY being down, not a bad request and not a broken
 * application - and 503 is the one status that tells a client "try again later"
 * truthfully.
 *
 * The document is left in FAILED with a safe reason, so a retry is a plain
 * re-POST to the process endpoint rather than a re-upload.
 *
 * THE MESSAGE NEVER CARRIES CHUNK TEXT. What is embedded is a passage of the
 * user's legal document; an exception message quoting it would put it into
 * every log that records the stack trace.
 */
public class DocumentEmbeddingException extends RuntimeException {

    public static final String CODE = "EMBEDDING_UNAVAILABLE";

    public DocumentEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
