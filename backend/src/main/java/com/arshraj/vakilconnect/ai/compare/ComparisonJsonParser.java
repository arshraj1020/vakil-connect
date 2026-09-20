package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the model's reply into a {@link ComparisonContent}, or refuses.
 *
 * A NEAR-DUPLICATE OF AI-4's AnalysisJsonParser, DELIBERATELY NOT SHARED. The
 * two parse different schemas (four keys here, six there) into types with
 * different identity guarantees, and factoring out "a strict JSON object
 * parser with clip-and-bound semantics" into a shared generic utility was
 * considered and rejected: the four rejection reasons below are the security-
 * relevant contract of THIS parser, and a shared helper would either have to
 * parameterise them (spreading the same risk across two features through one
 * seam) or hide them behind a level of indirection that makes "which field
 * names does this actually require" harder to answer by reading one file. Two
 * small, obviously-correct classes cost less than one clever one.
 *
 * See AnalysisJsonParser's class comment for the three things this
 * deliberately does NOT do (deserialise into the response type directly, hunt
 * for JSON inside prose, or fill in a missing key) - all three hold here for
 * the same reasons.
 */
@Component
public class ComparisonJsonParser {

    private static final Logger log = LoggerFactory.getLogger(ComparisonJsonParser.class);

    /**
     * FAIL_ON_TRAILING_TOKENS, again. The exact default that let Ollama's
     * NDJSON stream look like a successful one-token completion in AI-0 - see
     * ENGINEERING-NOTES.md. A private mapper, not the shared Spring one, for
     * the same reason AnalysisJsonParser uses its own: this strictness must
     * not change how every other HTTP request body in the application parses.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final String FENCE = "```";

    static final String FIELD_SUMMARY = "summary";
    static final String FIELD_KEY_DIFFERENCES = "keyDifferences";
    static final String FIELD_ONLY_IN_FIRST = "onlyInFirst";
    static final String FIELD_ONLY_IN_SECOND = "onlyInSecond";

    static final String REASON_EMPTY = "The model returned no comparison";
    static final String REASON_NOT_JSON = "The model did not return valid JSON";
    static final String REASON_NOT_AN_OBJECT = "The model did not return a JSON object";
    static final String REASON_MISSING_FIELD = "The model omitted a required field";
    static final String REASON_BAD_SUMMARY = "The model returned no usable summary";
    static final String REASON_NOT_AN_ARRAY = "The model returned a malformed list";
    static final String REASON_BAD_LIST_ENTRY = "The model returned a malformed list entry";

    private final AiComparisonProperties properties;

    public ComparisonJsonParser(AiComparisonProperties properties) {
        this.properties = properties;
    }

    public ComparisonContent parse(String reply) {
        if (reply == null || reply.isBlank()) {
            throw reject(REASON_EMPTY);
        }

        JsonNode root = readObject(stripFence(reply.strip()));

        return new ComparisonContent(
                summary(root),
                list(root, FIELD_KEY_DIFFERENCES),
                list(root, FIELD_ONLY_IN_FIRST),
                list(root, FIELD_ONLY_IN_SECOND));
    }

    private static String stripFence(String text) {
        if (!text.startsWith(FENCE) || !text.endsWith(FENCE) || text.length() <= 2 * FENCE.length()) {
            return text;
        }

        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }

        String withoutOpen = text.substring(firstNewline + 1);
        return withoutOpen.substring(0, withoutOpen.length() - FENCE.length()).strip();
    }

    private JsonNode readObject(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("Comparison reply was not parseable JSON ({} characters)", json.length());
            throw reject(REASON_NOT_JSON, e);
        }

        if (root == null || !root.isObject()) {
            throw reject(REASON_NOT_AN_OBJECT);
        }
        return root;
    }

    private String summary(JsonNode root) {
        JsonNode node = required(root, FIELD_SUMMARY);

        if (!node.isTextual()) {
            throw reject(REASON_BAD_SUMMARY);
        }

        String summary = node.asText().strip();
        if (summary.isEmpty()) {
            throw reject(REASON_BAD_SUMMARY);
        }

        return clip(summary, properties.maxSummaryCharacters());
    }

    private List<String> list(JsonNode root, String field) {
        JsonNode node = required(root, field);

        if (!node.isArray()) {
            throw reject(REASON_NOT_AN_ARRAY);
        }

        List<String> entries = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                throw reject(REASON_BAD_LIST_ENTRY);
            }

            String entry = element.asText().strip();
            if (entry.isEmpty()) {
                continue;
            }

            entries.add(clip(entry, properties.maxItemCharacters()));

            if (entries.size() == properties.maxListItems()) {
                log.debug("Comparison field '{}' truncated at {} entries", field, entries.size());
                break;
            }
        }

        return List.copyOf(entries);
    }

    private JsonNode required(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            log.warn("Comparison reply omitted required field '{}'", field);
            throw reject(REASON_MISSING_FIELD);
        }
        return node;
    }

    private static String clip(String value, int limit) {
        return value.length() <= limit
                ? value
                : value.substring(0, limit).stripTrailing() + "…";
    }

    private static AiAnswerUnavailableException reject(String reason) {
        return reject(reason, null);
    }

    private static AiAnswerUnavailableException reject(String reason, Throwable cause) {
        return new AiAnswerUnavailableException(reason, cause);
    }
}
