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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "vakilconnect.ai.provider",
        havingValue = AiProperties.GEMINI)
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    static final String API_KEY_HEADER = "x-goog-api-key";

    private final RestClient restClient;
    private final AiProperties properties;
    private final AiMetrics metrics;

    public GeminiLlmClient(@Qualifier("geminiRestClient") RestClient restClient,
                            AiProperties properties,
                            AiMetrics metrics) {

        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "vakilconnect.ai.provider=gemini requires AI_API_KEY to be set. "
                            + "Create a free key at https://aistudio.google.com/apikey");
        }

        this.restClient = restClient;
        this.properties = properties;
        this.metrics = metrics;

        log.info("AI provider is GEMINI using model {}. Hosted inference: requires "
                + "AI_API_KEY and is subject to Google's rate limits.", properties.model());
    }

    @Override
    public String providerName() {
        return AiProperties.GEMINI;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long startedAt = System.nanoTime();

        try {
            JsonNode body = restClient.post()
                    .uri(URI.create(endpointFor(properties.model())))
                    .header(API_KEY_HEADER, properties.apiKey())
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

            log.debug("Gemini completed operation {} ({} chars)",
                    request.operation(), response.text().length());

            return response;

        } catch (LlmException e) {
            metrics.recordFailure(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));
            throw e;

        } catch (ResourceAccessException e) {
            metrics.recordFailure(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));
            throw new LlmException(
                    "Gemini was not reachable (" + e.getMessage() + ") - check network access", e);

        } catch (RestClientException e) {
            metrics.recordFailure(providerName(), request.operation());
            metrics.recordDuration(providerName(), request.operation(), elapsed(startedAt));
            throw new LlmException(
                    "Gemini returned a response that could not be read as JSON: "
                            + e.getClass().getSimpleName(), e);
        }
    }

    static String endpointFor(String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent";
    }

    static boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }

    private LlmException describe(int status) {
        if (status == 401 || status == 403) {
            return new PermanentLlmException(
                    "Gemini rejected the configured AI_API_KEY (HTTP " + status + ")");
        }
        if (status == 404) {
            return new PermanentLlmException(
                    "Gemini has no model named '" + properties.model()
                            + "' - check vakilconnect.ai.model / AI_MODEL");
        }
        String detail = "Gemini returned HTTP " + status;
        return isRetryableStatus(status)
                ? new LlmException(detail)
                : new PermanentLlmException(detail);
    }

    private Map<String, Object> payload(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (request.hasSystemPrompt()) {
            body.put("systemInstruction",
                    Map.of("parts", List.of(Map.of("text", request.systemPrompt()))));
        }

        body.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", request.userPrompt())))));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", properties.temperature());
        generationConfig.put("maxOutputTokens", properties.maxOutputTokens());
        body.put("generationConfig", generationConfig);

        return body;
    }

    private LlmResponse parse(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new LlmException("Gemini returned an empty body");
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("UNKNOWN");
            throw new PermanentLlmException(
                    "Gemini returned no candidates, blockReason=" + blockReason);
        }

        JsonNode first = candidates.get(0);
        JsonNode parts = first.path("content").path("parts");
        StringBuilder text = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                text.append(part.path("text").asText(""));
            }
        }

        if (text.isEmpty()) {
            String finishReason = first.path("finishReason").asText("UNKNOWN");
            throw new PermanentLlmException(
                    "Gemini returned no text, finishReason=" + finishReason);
        }

        String model = root.path("modelVersion").asText(properties.model());
        return new LlmResponse(text.toString(), model.isBlank() ? properties.model() : model);
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
