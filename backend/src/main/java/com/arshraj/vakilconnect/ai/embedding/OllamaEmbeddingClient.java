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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embeds text through a LOCAL Ollama server.
 *
 * THE SAME SHAPE AS OllamaLlmClient, DELIBERATELY - same base URL, same error
 * classification, same "no startup probe" stance, same no-vendor-SDK approach.
 * /api/embeddings is one POST, and reusing the adapter pattern from AI-0 means
 * one error model rather than two sitting side by side. This is precisely why
 * langchain4j-ollama was not added.
 *
 * NO API KEY AND NO STARTUP CALL. There is nothing to authenticate, and Ollama
 * is a tool a developer starts and stops freely - an application that refused
 * to boot without it would be hostile, and CI has none. The cost is that "the
 * embedding model is not pulled" surfaces on the first ingestion, so the
 * messages below name the exact command.
 *
 * NEVER LOGS THE TEXT IT EMBEDS. The input is a chunk of the user's legal
 * document. Log lines carry the model, the count and the dimension.
 */
@Component
@ConditionalOnProperty(name = "vakilconnect.ai.embedding.provider",
        havingValue = AiEmbeddingProperties.OLLAMA)
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    static final String EMBEDDINGS_PATH = "/api/embeddings";

    /**
     * The AiMetrics `operation` tag for every embedding call.
     *
     * A FIXED LITERAL, never derived from a document, a filename or a user.
     * Tag values become time series, so an unbounded one is a cardinality
     * explosion and a personal one copies user data into a metrics store with
     * a completely different access-control model from the database.
     */
    public static final String EMBED_OPERATION = "embed";

    private final RestClient restClient;
    private final AiProperties aiProperties;
    private final AiEmbeddingProperties properties;
    /**
     * AI-0's AiMetrics, REUSED RATHER THAN DUPLICATED.
     *
     * Its meters are already tagged (provider, operation, outcome) and its
     * timer already answers "how slow is inference" - which is precisely the
     * question here, just with operation={@link #EMBED_OPERATION} instead of a
     * chat operation. A parallel EmbeddingMetrics class would publish the same
     * three measurements under different names, so a dashboard would need two
     * queries to answer one question.
     */
    private final AiMetrics metrics;

    public OllamaEmbeddingClient(@Qualifier("ollamaRestClient") RestClient restClient,
                                 AiProperties aiProperties,
                                 AiEmbeddingProperties properties,
                                 AiMetrics metrics) {

        this.restClient = restClient;
        this.aiProperties = aiProperties;
        this.properties = properties;
        this.metrics = metrics;

        log.info("Embedding provider is OLLAMA at {} using model {} ({} dimensions). "
                        + "Local inference: no API key and no cost. "
                        + "Ensure `ollama pull {}` has completed.",
                aiProperties.baseUrl(), properties.model(), properties.dimension(),
                properties.model());
    }

    @Override
    public String providerName() {
        return AiEmbeddingProperties.OLLAMA;
    }

    @Override
    public int dimension() {
        return properties.dimension();
    }

    /**
     * ONE CALL PER TEXT, because that is what Ollama's API offers - its
     * /api/embeddings takes a single `prompt`. The loop lives here rather than
     * in the pipeline so a provider that batches natively can override
     * embedAll() without any caller changing.
     *
     * FAILS ON THE FIRST ERROR rather than collecting partial results. The
     * pipeline needs all-or-nothing: a document with 40 of its 50 chunks
     * embedded is worse than one with none, because retrieval would silently
     * miss the last fifth and nothing would say so.
     */
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
                    .uri(URI.create(endpointFor(aiProperties.baseUrl())))
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
                    "Ollama is not reachable at " + aiProperties.baseUrl()
                            + " - is `ollama serve` running? (" + e.getMessage() + ")", e);

        } catch (RestClientException e) {
            // MUST BE LAST: ResourceAccessException is a subclass. A success
            // status whose body could not be read - without this the raw
            // exception would escape the pipeline's typed handling.
            metrics.recordFailure(providerName(), EMBED_OPERATION);
            metrics.recordDuration(providerName(), EMBED_OPERATION, elapsed(startedAt));
            throw new EmbeddingException(
                    "Ollama returned an unreadable embedding response: "
                            + e.getClass().getSimpleName(), e);
        }
    }

    private static java.time.Duration elapsed(long startedAtNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    static String endpointFor(String baseUrl) {
        return baseUrl + EMBEDDINGS_PATH;
    }

    static boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }

    /**
     * Maps a status onto a typed exception with an ACTIONABLE message.
     *
     * THE BODY IS NEVER READ. Ollama echoes parts of the request in its error
     * bodies, and the request here is a chunk of the user's document. The 404
     * case names the model from OUR configuration instead - which is not user
     * data - and tells the developer what to run.
     */
    private EmbeddingException describe(int status) {
        if (status == 404) {
            return new PermanentEmbeddingException(
                    "Ollama has no embedding model named '" + properties.model()
                            + "' - run: ollama pull " + properties.model());
        }
        String detail = "Ollama returned HTTP " + status + " for an embedding request";
        return isRetryableStatus(status)
                ? new EmbeddingException(detail)
                : new PermanentEmbeddingException(detail);
    }

    private Map<String, Object> payload(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("prompt", text);
        return body;
    }

    /**
     * Reads the vector, and CHECKS ITS WIDTH.
     *
     * The dimension check is the important half. `ai_document_chunks.embedding`
     * is `vector(768)`; a vector of any other length is rejected by PostgreSQL
     * on insert, one row at a time, after every chunk has already been embedded.
     * Catching it here turns "ERROR: expected 768 dimensions, not 1024" arriving
     * halfway through a write into "the configured model does not produce the
     * configured dimension", raised on the first chunk.
     *
     * Permanent, not transient: the same model will return the same width
     * forever, so retrying is pure waste.
     */
    private Embedding parse(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new EmbeddingException("Ollama returned an empty embedding body");
        }
        if (!root.path("error").asText("").isEmpty()) {
            throw new PermanentEmbeddingException(
                    "Ollama reported an error for embedding model '" + properties.model() + "'");
        }

        JsonNode values = root.path("embedding");
        if (!values.isArray() || values.isEmpty()) {
            throw new PermanentEmbeddingException(
                    "Ollama returned no embedding array - is '" + properties.model()
                            + "' an embedding model?");
        }

        int expected = properties.dimension();
        if (values.size() != expected) {
            throw new PermanentEmbeddingException(
                    "Model '" + properties.model() + "' returned " + values.size()
                            + " dimensions but " + expected + " are configured. "
                            + "Set vakilconnect.ai.embedding.dimension to match the model, "
                            + "and remember the vector(n) column in V9 must match too.");
        }

        float[] vector = new float[expected];
        for (int i = 0; i < expected; i++) {
            JsonNode value = values.get(i);
            if (!value.isNumber()) {
                throw new PermanentEmbeddingException(
                        "Ollama returned a non-numeric value at embedding position " + i);
            }
            vector[i] = (float) value.asDouble();
        }

        return new Embedding(vector, properties.model());
    }
}
