package com.arshraj.vakilconnect.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls a LOCAL Ollama server's chat API.
 *
 * WHY LOCAL INFERENCE IS THE DEFAULT REAL PROVIDER. VakilConnect's AI layer has
 * to be fully demonstrable with no billing account, no API key and no paid
 * service - so the provider that proves the abstraction works must be one that
 * runs on the developer's own machine. Ollama needs no credential of any kind,
 * which is why {@link AiProperties} has no credential component at all.
 *
 * THE ONLY CLASS IN THE CODEBASE THAT KNOWS OLLAMA EXISTS. Nothing else names
 * the endpoint or knows the JSON shape. Adding a paid provider later is a new
 * class implementing {@link LlmClient} plus a property value - no caller
 * changes, which is the entire reason the interface exists.
 *
 * NO VENDOR SDK AND NO FRAMEWORK, following ResendEmailSender's precedent.
 * /api/chat is one POST with a small, stable JSON body; RestClient and Jackson
 * both ship with spring-boot-starter-web. This keeps the dependency count for
 * the whole AI foundation at ZERO.
 *
 * NO API KEY, AND NO STARTUP HEALTH CHECK. There is nothing to authenticate, so
 * unlike a paid adapter there is no credential to fail fast on. Nor does this
 * ping Ollama at boot: Ollama is a local development tool that a developer
 * starts and stops freely, and an application that refused to start because
 * `ollama serve` was not running would be hostile. The cost of that choice is
 * that "Ollama is down" surfaces on the first request instead - so the messages
 * below are written to say exactly what to do about it.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.ai.provider",
        havingValue = AiProperties.OLLAMA)
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

    /** Ollama's OpenAI-shaped chat endpoint. Relative to the configured base URL. */
    static final String CHAT_PATH = "/api/chat";

    private final RestClient restClient;
    private final AiProperties properties;
    private final AiMetrics metrics;

    /**
     * @param restClient the finished client, NOT a builder. See {@link AiConfig}
     *                   for why: setting the request factory here would
     *                   overwrite the one MockRestServiceServer installs, and a
     *                   unit test would reach a real Ollama server.
     */
    public OllamaLlmClient(@Qualifier("ollamaRestClient") RestClient restClient,
                           AiProperties properties,
                           AiMetrics metrics) {

        /*
         * The one thing worth failing fast on. There is no key to check, but a
         * base URL that is not a URL would throw from URI.create on the first
         * request - a runtime failure for what is unambiguously a startup-time
         * configuration mistake.
         */
        String endpoint = endpointFor(properties.baseUrl());
        try {
            URI parsed = URI.create(endpoint);
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                throw new IllegalArgumentException("missing scheme or host");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "vakilconnect.ai.provider=ollama requires OLLAMA_BASE_URL to be an "
                            + "absolute http(s) URL such as http://localhost:11434, but got: "
                            + properties.baseUrl(), e);
        }

        this.restClient = restClient;
        this.properties = properties;
        this.metrics = metrics;

        log.info("AI provider is OLLAMA at {} using model {}. "
                        + "Local inference: no API key and no cost. "
                        + "Ensure `ollama serve` is running and `ollama pull {}` has completed.",
                properties.baseUrl(), properties.model(), properties.model());
    }

    @Override
    public String providerName() {
        return AiProperties.OLLAMA;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long startedAt = System.nanoTime();

        try {
            JsonNode body = restClient.post()
                    .uri(URI.create(endpointFor(properties.baseUrl())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(request))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, response) -> {
                        throw describe(response.getStatusCode().value());
                    })
                    .body(JsonNode.class);

            LlmResponse response = parse(body);

            metrics.recordSuccess(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));

            // Operation and size only. Never the prompt, never the completion.
            log.debug("Ollama completed operation {} ({} chars)",
                    request.operation(), response.text().length());

            return response;

        } catch (LlmException e) {
            metrics.recordFailure(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));
            throw e;

        } catch (ResourceAccessException e) {
            metrics.recordFailure(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));
            /*
             * BY FAR THE MOST COMMON FAILURE, and the message is the whole
             * value of catching it separately. Connection refused to localhost
             * means Ollama is not running, and a developer seeing
             * "ResourceAccessException: Connection refused" has to go and find
             * that out. Naming the URL and the command turns a five-minute
             * detour into a five-second one.
             *
             * Transient because it is: starting Ollama fixes it, and nothing
             * about the request was wrong.
             */
            throw new LlmException(
                    "Ollama is not reachable at " + properties.baseUrl()
                            + " - is `ollama serve` running? (" + e.getMessage() + ")", e);

        } catch (RestClientException e) {
            /*
             * MUST BE LAST: ResourceAccessException is a subclass, so the
             * narrower catch above has to come first or it would be unreachable.
             *
             * Reached when the status was a success but the body could not be
             * read as JSON. The realistic cause here is a STREAMING response -
             * Ollama streams newline-delimited JSON by default, so if `stream:
             * false` were ever dropped from the payload the body would be many
             * JSON objects rather than one. Without this clause that would
             * escape as a raw RestClientException, breaking the exception
             * contract LlmClient documents and skipping the failure counter, so
             * a broken integration would look like NO TRAFFIC on the dashboard.
             */
            metrics.recordFailure(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));
            throw new LlmException(
                    "Ollama returned a response that could not be read as a single JSON "
                            + "object (is streaming enabled?): " + e.getClass().getSimpleName(), e);
        }
    }

    // ----------------------------------------------------------------- URL

    /**
     * Package-private so a test can assert the exact URL without duplicating the
     * string. The base URL arrives already stripped of trailing slashes by
     * {@link AiProperties}' compact constructor.
     */
    static String endpointFor(String baseUrl) {
        return baseUrl + CHAT_PATH;
    }

    /**
     * 5xx and 429 are transient; every other 4xx is permanent.
     *
     * Identical classification to {@code ResendEmailSender.isRetryableStatus},
     * kept separate because the two integrations are free to disagree later.
     */
    static boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }

    /**
     * Maps a status onto a typed exception with an ACTIONABLE message.
     *
     * THE RESPONSE BODY IS NEVER READ. Ollama's error bodies are
     * {@code {"error": "..."}} and can echo parts of the request, and the
     * request is the user's prompt - so quoting it would put user content into
     * every log that records this stack trace. The 404 case therefore names the
     * model from OUR OWN CONFIGURATION, which is not user data, rather than
     * repeating what the server said.
     */
    private LlmException describe(int status) {
        if (status == 404) {
            /*
             * The second most common failure after "Ollama is not running", and
             * the one whose default message is least helpful. Ollama answers 404
             * when the model tag has not been pulled.
             */
            return new PermanentLlmException(
                    "Ollama has no model named '" + properties.model()
                            + "' - run: ollama pull " + properties.model());
        }
        String detail = "Ollama returned HTTP " + status;
        return isRetryableStatus(status)
                ? new LlmException(detail)
                : new PermanentLlmException(detail);
    }

    // ------------------------------------------------------------- request

    /**
     * Ollama's documented /api/chat shape. LinkedHashMap for a stable field
     * order, which is what makes the request assertable in tests.
     */
    private Map<String, Object> payload(LlmRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();

        /*
         * OMITTED WHEN ABSENT rather than sent as an empty system message. An
         * empty system turn is not an error here, but it is a wasted turn that
         * some models treat as a real instruction to say nothing.
         */
        if (request.hasSystemPrompt()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages);

        /*
         * NOT OPTIONAL. Ollama STREAMS BY DEFAULT, returning newline-delimited
         * JSON - one object per token. Omitting this would make every response
         * unparseable as a single JSON object, and the failure would look like
         * a mysterious deserialisation error rather than a missing flag. A test
         * asserts this field is present and false.
         */
        body.put("stream", false);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", properties.temperature());
        // Ollama's name for a maximum generated-token count.
        options.put("num_predict", properties.maxOutputTokens());
        body.put("options", options);

        return body;
    }

    // ------------------------------------------------------------ response

    /**
     * NAVIGATED WITH path(), NOT get(). Every hop returns a missing node rather
     * than null when a field is absent, so a response shape we did not expect
     * produces a described PermanentLlmException instead of a
     * NullPointerException from somewhere three frames deep.
     */
    private LlmResponse parse(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new LlmException("Ollama returned an empty body");
        }

        /*
         * Ollama sometimes reports a problem in a 200 body rather than in the
         * status - a model that failed to load, for instance. The error TEXT is
         * not quoted for the usual reason: it can echo the request.
         */
        if (!root.path("error").asText("").isEmpty()) {
            throw new PermanentLlmException(
                    "Ollama reported an error for model '" + properties.model()
                            + "' - check the Ollama server log");
        }

        /*
         * PARTIAL-RESPONSE GUARD, and it protects against something nastier
         * than an exception.
         *
         * If `stream: false` were ever dropped from the payload, Ollama would
         * return newline-delimited JSON - one object per token. Jackson does NOT
         * fail on trailing content by default, so it would happily parse the
         * FIRST object and discard the rest. The call would look like a success
         * and return a ONE-TOKEN ANSWER: silent truncation, which no status code
         * and no exception would ever reveal.
         *
         * A non-streamed response always carries done=true. Absent is treated as
         * true so a version that stops sending the field does not break us; an
         * explicit false is what a streamed chunk looks like, and that is a
         * defect in this adapter rather than a transient condition - hence
         * permanent.
         */
        if (!root.path("done").asBoolean(true)) {
            throw new PermanentLlmException(
                    "Ollama returned a partial, streamed response (done=false). "
                            + "The request must send stream:false, or answers are "
                            + "silently truncated to the first token.");
        }

        String text = root.path("message").path("content").asText("");
        if (text.isBlank()) {
            /*
             * done_reason is a fixed category ("stop", "length", "load"), not
             * user content, so naming it is safe and is the only thing that
             * makes an empty completion debuggable.
             */
            throw new PermanentLlmException("Ollama returned no text, done_reason="
                    + root.path("done_reason").asText("UNKNOWN"));
        }

        /*
         * The model that actually answered. Not merely an echo of configuration:
         * `llama3.2` resolves to whichever build is pulled locally, so when
         * output quality changes with no config change, this is the evidence.
         */
        String model = root.path("model").asText(properties.model());
        return new LlmResponse(text, model.isBlank() ? properties.model() : model);
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
