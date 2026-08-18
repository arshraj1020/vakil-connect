package com.arshraj.vakilconnect.ai;

/**
 * A PERMANENT model failure. Retrying is pointless.
 *
 * Thrown for:
 *
 *   - 4xx other than 429 - a malformed request, a rejected or revoked API key,
 *     a model identifier the provider has retired. Every one of these fails
 *     identically on the second and third attempt.
 *
 *   - A SUPPRESSED COMPLETION. The provider's safety filter blocked the prompt
 *     or the response. This is classed permanent because the same prompt will
 *     be blocked again every time; it is a content decision, not an outage.
 *     A caller that wants to recover must change what it asked, not ask again.
 *
 *   - A response containing no usable text. Structurally a success, practically
 *     a failure, and one that will recur for the same input.
 *
 * Distinguishing this from {@link LlmException} is what will let a retry policy
 * be added later without also retrying the things that must never be retried.
 */
public class PermanentLlmException extends LlmException {

    public PermanentLlmException(String message) {
        super(message);
    }

    public PermanentLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
