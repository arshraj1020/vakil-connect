package com.arshraj.vakilconnect.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Everything the AI layer is allowed to be configured with, in one bound object.
 *
 * Bound exactly like {@code IdentityProperties} and {@code EmailProperties}: a
 * validated record, registered explicitly on the application class, with
 * DEFAULTS LIVING IN application.yaml as {@code ${ENV_VAR:default}} rather than
 * as {@code @DefaultValue} here - one visible, greppable, environment-
 * overridable source per key.
 *
 * OLLAMA (THE DEFAULT REAL PROVIDER) NEEDS NO CREDENTIAL: it runs locally and
 * authenticates nothing, so VakilConnect's AI layer is fully usable with no API
 * key, no billing account and no paid service. GEMINI IS THE EXCEPTION: a
 * hosted provider, added so the feature works from a deployed environment (such
 * as Render) where nothing can reach a local Ollama server. Its key lives in
 * {@code apiKey} below, blank for every other provider.
 *
 * apiKey IS WHY THIS RECORD OVERRIDES toString() - see the override below and
 * EmailProperties, which does the same for the identical reason.
 *
 * WHY THE MODEL AND BASE URL ARE CONFIGURATION AND NOT CONSTANTS. Ollama serves
 * on a port the developer controls and hosts whichever models they have pulled.
 * Hard-coding either would make "I run Ollama on a different port" or "I pulled
 * a smaller model because my laptop has 8GB" a code change instead of an
 * environment variable.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai")
public record AiProperties(

        /*
         * `stub` or `ollama`. @NotBlank because an empty provider would silently
         * produce a context with NO LlmClient bean at all, and the failure would
         * surface later as an unsatisfied dependency rather than as the
         * configuration mistake it is. Same reasoning as EmailProperties.provider.
         */
        @NotBlank
        String provider,

        /*
         * Where Ollama is listening, e.g. http://localhost:11434.
         *
         * NORMALISED IN THE COMPACT CONSTRUCTOR BELOW - a trailing slash here
         * would produce `http://localhost:11434//api/chat`, which some proxies
         * reject and which makes an exact-URL assertion in a test misleading.
         */
        @NotBlank
        String baseUrl,

        /*
         * Ollama model tag, e.g. `llama3.2`. Must already be pulled - the
         * adapter does not pull models, because a first request that silently
         * downloaded several gigabytes would look like a hang.
         */
        @NotBlank
        String model,

        /*
         * 0.0 is deterministic, 2.0 is the practical maximum. Bounded here
         * because an out-of-range value is rejected at CALL time - which means
         * the first person to find out is a user, mid request. Validation turns
         * that into a startup failure.
         */
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("2.0")
        Double temperature,

        /*
         * Hard ceiling on generated tokens, sent to Ollama as `num_predict`.
         *
         * On a paid API this is a cost control. On local inference it is a TIME
         * control, which matters more: every token is CPU or GPU work on the
         * developer's own machine, so an unbounded generation is a request that
         * appears to hang rather than an unexpected invoice.
         */
        @NotNull
        @Min(1)
        Integer maxOutputTokens,

        /* TCP connect timeout. Short: to localhost this either succeeds
         * immediately or Ollama is not running, and waiting longer fixes
         * neither. */
        @NotNull
        Duration connectTimeout,

        /*
         * Read timeout - how long we wait for the model to finish.
         *
         * MUCH LARGER THAN ANY OTHER TIMEOUT IN THIS CODEBASE, and larger than
         * a hosted API would need. Local inference on CPU is slow: a 3B model
         * answering a paragraph can take tens of seconds, and a larger one on a
         * cold start can exceed a minute. A value tuned for a cloud provider
         * would manufacture failures out of perfectly healthy local calls.
         *
         * It is nonetheless FINITE, because RestClient's default is not, and a
         * hung connection with no read timeout holds a request thread until the
         * process restarts.
         */
        @NotNull
        Duration readTimeout,

        /*
         * API key for a HOSTED provider such as Gemini. Blank for stub and
         * ollama, which authenticate nothing - required only when
         * provider=gemini, and the fail-fast check for that lives in
         * GeminiLlmClient's constructor, the same pattern EmailProperties.apiKey
         * uses for ResendEmailSender. NOT @NotBlank here: making it mandatory
         * would stop every local-inference developer's application from
         * starting.
         *
         * ADDING THIS FIELD IS WHY AiProperties NOW HAS A REDACTING toString()
         * BELOW - see EmailProperties for the identical hazard and fix.
         */
        String apiKey
) {

    public static final String STUB = "stub";
    public static final String OLLAMA = "ollama";
    public static final String GEMINI = "gemini";

    /**
     * Strips trailing slashes from the base URL at BIND time, so every consumer
     * downstream sees one canonical form.
     *
     * Doing it here rather than at each call site means `http://localhost:11434`
     * and `http://localhost:11434/` are the same configuration, which is what a
     * developer copying a URL out of a terminal will assume. A base URL of only
     * slashes normalises to the empty string and is then caught by @NotBlank,
     * which is the correct outcome - it was never a URL.
     */
    public AiProperties {
        if (baseUrl != null) {
            baseUrl = baseUrl.strip();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
    }

    /**
     * REDACTED, the same hazard EmailProperties.toString() exists to close.
     *
     * A record's generated toString() prints every component, so once apiKey
     * exists a Gemini key would otherwise land in any log line, stack trace or
     * debugger frame that formatted this object.
     */
    @Override
    public String toString() {
        return "AiProperties{provider=" + provider
                + ", baseUrl=" + baseUrl
                + ", model=" + model
                + ", temperature=" + temperature
                + ", maxOutputTokens=" + maxOutputTokens
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", apiKey=<redacted>}";
    }
}
