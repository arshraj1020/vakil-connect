package com.arshraj.vakilconnect.ai;

/**
 * What the model said.
 *
 * @param text  The generated text. Never null and never blank - an
 *              implementation that receives an empty or suppressed completion
 *              throws {@link PermanentLlmException} rather than handing back a
 *              hollow success that a caller has to re-check.
 * @param model The model that actually answered, as reported by the provider.
 *              This is NOT simply an echo of the configured value: a tag like
 *              `llama3.2` resolves to whichever build is pulled locally, so
 *              when output quality changes with no configuration change on our
 *              side, this field is the evidence.
 */
public record LlmResponse(String text, String model) {

    public LlmResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "text must not be blank: an empty completion is a failure, not a response");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }

    /**
     * REDACTED, for the same reason {@link LlmRequest#toString()} is.
     *
     * Model output is derived from user input and routinely quotes it back, so
     * it carries the same disclosure risk as the prompt. A generated toString()
     * would leak it into any log line that formatted this object.
     */
    @Override
    public String toString() {
        return "LlmResponse{model=" + model
                + ", textChars=" + text.length()
                + ", text=<redacted>}";
    }
}
