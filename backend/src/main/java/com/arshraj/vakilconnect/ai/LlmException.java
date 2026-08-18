package com.arshraj.vakilconnect.ai;

/**
 * A TRANSIENT model failure. Another attempt could plausibly succeed.
 *
 * Thrown for 5xx, 429, and connection/read timeouts.
 *
 * Permanent failures use {@link PermanentLlmException}, a subclass. A SUBCLASS
 * RATHER THAN A BOOLEAN FLAG, exactly as with {@code EmailSendException}:
 * Spring Retry classifies on exception TYPE, so encoding the decision in the
 * type makes any future retry policy compile-checked instead of dependent on a
 * SpEL expression that can only fail at runtime.
 *
 * NO RETRY POLICY IS ATTACHED AT AI-0, and that is deliberate. Whether to retry
 * depends entirely on who is calling: a background summarisation job should
 * back off and try again, while a user waiting on a chat response is better
 * served by a fast failure than by three attempts and eight seconds of backoff.
 * There is no caller yet, so there is nothing to make that choice on behalf of.
 * The type split is what makes the choice cheap to add later at the call site.
 *
 * NEVER CARRIES THE PROMPT, THE COMPLETION, OR THE PROVIDER'S RESPONSE BODY.
 * Only the status and a fixed description. A provider error body can echo the
 * request, and the request is user content - so putting it in an exception
 * message puts it in every log that records the stack trace.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    /** True unless this is a permanent failure. */
    public boolean isRetryable() {
        return !(this instanceof PermanentLlmException);
    }
}
