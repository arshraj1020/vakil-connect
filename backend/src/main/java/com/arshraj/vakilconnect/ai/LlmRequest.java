package com.arshraj.vakilconnect.ai;

/**
 * One prompt, plus the label its metrics are filed under.
 *
 * @param operation LOW-CARDINALITY, DEVELOPER-CHOSEN label such as
 *                  "smoke-test" or "intake-summary". It becomes a Micrometer
 *                  tag, and a tag value becomes a time series - so it must
 *                  NEVER be a user id, an email address, a document name or
 *                  anything else user-supplied. An unbounded tag is a
 *                  cardinality explosion, and a personal one leaks PII into a
 *                  metrics store whose access-control model is nothing like the
 *                  database's.
 * @param systemPrompt Instructions to the model. Nullable; omitted from the
 *                  request entirely when absent rather than sent as an empty
 *                  string, which some providers treat as a validation error.
 * @param userPrompt The prompt itself. Required.
 */
public record LlmRequest(String operation, String systemPrompt, String userPrompt) {

    public LlmRequest {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException(
                    "operation is required: it is the metric tag for this call");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }
    }

    /** A prompt with no system instruction. */
    public static LlmRequest of(String operation, String userPrompt) {
        return new LlmRequest(operation, null, userPrompt);
    }

    public static LlmRequest of(String operation, String systemPrompt, String userPrompt) {
        return new LlmRequest(operation, systemPrompt, userPrompt);
    }

    /** True when a system instruction should be sent. */
    public boolean hasSystemPrompt() {
        return systemPrompt != null && !systemPrompt.isBlank();
    }

    /**
     * REDACTED. PROMPTS ARE USER CONTENT AND ARE TREATED AS SENSITIVE.
     *
     * This is the same hazard as EmailProperties' API key, with a wider blast
     * radius. A record prints every component from its generated toString(), so
     * one {@code log.debug("request={}", request)} - or a single stack trace
     * from a framework that formats its arguments - would put a user's
     * description of their legal problem into the application log, which on
     * Render is retained, searchable, and readable by anyone with dashboard
     * access. That is a completely different access-control model from the
     * database the data came from.
     *
     * Lengths are kept because they are genuinely useful when debugging a
     * truncation or a token-limit failure, and a character count discloses
     * nothing.
     */
    @Override
    public String toString() {
        return "LlmRequest{operation=" + operation
                + ", systemPromptChars=" + (systemPrompt == null ? 0 : systemPrompt.length())
                + ", userPromptChars=" + userPrompt.length()
                + ", prompts=<redacted>}";
    }
}
