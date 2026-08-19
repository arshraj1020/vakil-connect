package com.arshraj.vakilconnect.ai.embedding;

/**
 * A TRANSIENT embedding failure. Another attempt could plausibly succeed.
 *
 * Thrown for 5xx, 429, and connection/read timeouts.
 *
 * DELIBERATELY NOT LlmException. The two services fail for overlapping but
 * distinct reasons - an embedding model that is not pulled is not the same
 * incident as a chat model that is not pulled, and a caller that wants to retry
 * one may not want to retry the other. Sharing the type would make those
 * indistinguishable at the catch site, which is where the decision is made.
 *
 * Permanent failures use {@link PermanentEmbeddingException}, a subclass, so a
 * future retry policy classifies on TYPE and is compile-checked - the same
 * split EmailSendException and LlmException use.
 *
 * NEVER CARRIES DOCUMENT TEXT. Only the status and a fixed description. The
 * input to an embedding call is a chunk of the user's legal document, so a
 * message that echoed it would put that text into every log recording the
 * stack trace.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }

    /** True unless this is a permanent failure. */
    public boolean isRetryable() {
        return !(this instanceof PermanentEmbeddingException);
    }
}
