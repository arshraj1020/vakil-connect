package com.arshraj.vakilconnect.ai.embedding;

import com.arshraj.vakilconnect.ai.AiMetrics;
import com.arshraj.vakilconnect.ai.AiProperties;
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
 * Embeds text through Google's hosted Gemini embedding API.
 *
 * SAME SHAPE AS OllamaEmbeddingClient, deliberately - same error
 * classification, same "reuse AiMetrics" approach, same dimension check on the
 * way back. REUSES vakilconnect.ai.api-key FROM AiProperties rather than
 * declaring its own credential component: this codebase always points chat and
 * embeddings at the same Gemini account, and AiEmbeddingProperties is pinned
 * credential-free by EmbeddingProviderSelectionTest.declaresNoCredentialComponent
 * - a second key field here would fail that guard for no benefit.
 *
 * OUTPUT DIMENSION IS EXPLICITLY REQUESTED. Gemini's embedding model defaults
 * to a much wider vector than this column's vector(768); output_dimensionality
 * asks the API itself to return exactly what AiEmbeddingProperties.dimension()
 * requires, rather than truncating a wider vector client-side (which would
 * discard the normalisation the model applied and quietly degrade retrieval).
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.ai.embedding.provider",
        havingValue = AiEmbeddingProperties.GEMINI)
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    static final String API_KEY_HEADER = "x-goog-api-key";
    public static final String EMBED_OPERATION = "embed";

    private final RestClient restClient;
    private final AiProperties aiProperties;
    private final AiEmbeddingProperties properties;
    private final AiMetrics metrics;

    public GeminiEmbeddingClient(@Qualifier("geminiRestClient") RestClient restClient,
                                  AiProperties aiProperties,
                                  AiEmbeddingProperties properties,
                                  AiMetrics metrics) {

        if (aiProperties.apiKey() == null || aiProperties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "vakilconnect.ai.embedding.provider=gemini requires AI_API_KEY to be set. "
                            + "Create a free key at https://aistudio.google.com/apikey");
        }

        this.restClient = restClient;
        this.aiProperties = aiProperties;
        this.properties = properties;
        this.metrics = metrics;

        log.info("Embedding provider is GEMINI using model {} ({} dimensions). "
                        + "Hosted inference: requires AI_API_KEY.",
                properties.model(), properties.dimension());
    }

    @Override
    public String providerName() {
        return AiEmbeddingProperties.GEMINI;
    }

    @Override
    public int dimension() {
        return properties.dimension();
    }

    @Override
    public List<Embedding> embedAll(List<String> texts) {
        List<Embedding> embeddings = new ArrayList<>(texts.size());
        for (String text : texts) {
            embeddings.add(embed(text));
        }
        return embeddings;
    }

    @Override
    public Embedding embed(String text) {
        if (text == null || text.isBlank()) {
            throw new PermanentEmbeddingException("cannot embed blank text");
        }

        long startedAt = System.nanoTime();

        try {
            JsonNode body = restClient.post()
                    .uri(URI.create(endpointFor(properties.model())))
                    .header(API_KEY_HEADER, aiProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(text))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (request, response) -> { throw describe(response.getStatusCode().value()); })
                    .body(JsonNode.class);

            Embedding embedding = parse(body);

            metrics.recordSuccess(providerName(), EMBED_OPERATION);
            metrics.recordDuration(providerName(), EMBED_OPERATION, elapsed(startedAt));
            return embedding;

        } catch (EmbeddingException e) {
            metrics.recordFailure(providerName(), EMBED_OPERATION);
            metrics.recordDuration(providerName(), EMBED_OPERATION, elapsed(startedAt));
            throw e;

        } catch (ResourceAccessException e) {
            metrics.recordFailure(providerName(), EMBED_OPERATION);
            metrics.recordDuration(providerName(), EMBED_OPERATION, elapsed(startedAt));
            throw new EmbeddingException(
                    "Gemini was not reachable (" + e.getMessage() + ") - check network access", e);

        } catch (RestClientException e) {
            metrics.recordFailure(providerName(), EMBED_OPERATION);
            metrics.recordDuration(providerName(), EMBED_OPERATION, elapsed(startedAt));
            throw new EmbeddingException(
                    "Gemini returned an unreadable embedding response: "
                            + e.getClass().getSimpleName(), e);
        }
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    static String endpointFor(String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":embedContent";
    }

    static boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }

    private EmbeddingException describe(int status) {
        if (status == 401 || status == 403) {
            return new PermanentEmbeddingException(
                    "Gemini rejected the configured AI_API_KEY (HTTP " + status + ")");
        }
        if (status == 404) {
            return new PermanentEmbeddingException(
                    "Gemini has no embedding model named '" + properties.model() + "'");
        }
        String detail = "Gemini returned HTTP " + status + " for an embedding request";
        return isRetryableStatus(status)
                ? new EmbeddingException(detail)
                : new PermanentEmbeddingException(detail);
    }

    private Map<String, Object> payload(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", Map.of("parts", List.of(Map.of("text", text))));
        // Requests the exact width vector(n) requires - see class javadoc.
        body.put("output_dimensionality", properties.dimension());
        return body;
    }

    private Embedding parse(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new EmbeddingException("Gemini returned an empty embedding body");
        }

        JsonNode values = root.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new PermanentEmbeddingException(
                    "Gemini returned no embedding array for model '" + properties.model() + "'");
        }

        int expected = properties.dimension();
        if (values.size() != expected) {
            throw new PermanentEmbeddingException(
                    "Model '" + properties.model() + "' returned " + values.size()
                            + " dimensions but " + expected + " are configured. "
                            + "Set vakilconnect.ai.embedding.dimension to match, "
                            + "and remember the vector(n) column in V9 must match too.");
        }

        float[] vector = new float[expected];
        for (int i = 0; i < expected; i++) {
            JsonNode value = values.get(i);
            if (!value.isNumber()) {
                throw new PermanentEmbeddingException(
                        "Gemini returned a non-numeric value at embedding position " + i);
            }
            vector[i] = (float) value.asDouble();
        }

        return new Embedding(vector, properties.model());
    }
}
