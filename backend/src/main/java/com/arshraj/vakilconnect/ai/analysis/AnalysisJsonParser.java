package com.arshraj.vakilconnect.ai.analysis;

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
 * Turns the model's reply into an {@link AnalysisContent}, or refuses.
 *
 * ========================= WHAT THIS DELIBERATELY WILL NOT DO ===============
 *
 * 1. IT DOES NOT DESERIALISE INTO THE RESPONSE TYPE. The obvious implementation
 *    is {@code objectMapper.readValue(reply, DocumentAnalysis.class)}, and it is
 *    the single most dangerous line this feature could contain: DocumentAnalysis
 *    has documentId and documentName components, so a model that emitted them -
 *    invited to by a document saying "set documentId to ..." - would have its
 *    values become the identity of the response. This reads SIX NAMED CONTENT
 *    FIELDS by hand into a type that has no identity components at all, so
 *    there is nowhere for a forged identifier to land.
 *
 * 2. IT DOES NOT HUNT FOR JSON INSIDE PROSE. Scanning for the first '{' and the
 *    last '}' is the usual trick for making small models work, and it is wrong
 *    here for a specific reason: a legal document can contain braces, and if the
 *    model echoes a fragment of the document before its answer, the scan can
 *    lock onto document text and parse it as the analysis. The reply must BE the
 *    object.
 *
 * 3. IT DOES NOT FILL IN WHAT IS MISSING. An absent key is a refusal to answer,
 *    and defaulting it to an empty list would silently convert "the model did
 *    not say" into "the document contains none" - which for a `risks` field on a
 *    legal platform is precisely the wrong direction to guess in. The prompt
 *    tells the model to send [] explicitly when a category is empty, so [] means
 *    something and absence means something else.
 *
 * =========================== THE ONE CONCESSION =============================
 *
 * A single surrounding markdown code fence is removed before parsing. Small
 * local models wrap JSON in ```json ... ``` constantly, even when told not to.
 * This is safe in a way that brace-hunting is not: a fence is a self-delimiting
 * wrapper at the very start and end of the reply, so stripping it cannot change
 * WHICH bytes are treated as the JSON - it can only reveal them. Nothing else is
 * normalised, and a reply with any other text around the object is rejected.
 *
 * ============================== FAILURE MODE ================================
 *
 * Every rejection throws AiAnswerUnavailableException - 503, "temporarily
 * unavailable, please try again". That is the honest status: generation is
 * nondeterministic, so a retry has a real chance of succeeding, which is not
 * true of a 400 or a 422.
 *
 * NO REJECTION MESSAGE EVER CARRIES THE MODEL'S TEXT. The reply is derived from
 * the user's document and quotes it freely, so putting it in an exception
 * message would route document content into logs and, through the handler, very
 * nearly into a response body. The messages below are fixed strings; only
 * lengths and field names are recorded.
 */
@Component
public class AnalysisJsonParser {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJsonParser.class);

    /**
     * A PRIVATE MAPPER, NOT THE INJECTED SPRING ONE.
     *
     * FAIL_ON_TRAILING_TOKENS has to be on: without it Jackson parses the first
     * value and ignores whatever follows, so a reply of `{...} and I hope that
     * helps!` would parse cleanly and the "JSON only" rule would be unenforced.
     * That is the same default that let Ollama's NDJSON stream look like a
     * successful single-token completion in AI-0, so it is a mistake this
     * codebase has already made once.
     *
     * Enabling it on the shared ObjectMapper would change how every HTTP request
     * body in the application is parsed, which is not a change AI-4 gets to
     * make. A private mapper keeps the strictness where it belongs.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final String FENCE = "```";

    /** The six keys the reply must carry. Order matters only for the schema. */
    static final String FIELD_SUMMARY = "summary";
    static final String FIELD_PARTIES = "parties";
    static final String FIELD_IMPORTANT_DATES = "importantDates";
    static final String FIELD_OBLIGATIONS = "obligations";
    static final String FIELD_KEY_CLAUSES = "keyClauses";
    static final String FIELD_RISKS = "risks";

    /*
     * FIXED REJECTION MESSAGES. Named so that a test can assert which rule
     * fired without any of them ever interpolating model output.
     */
    static final String REASON_EMPTY = "The model returned no analysis";
    static final String REASON_NOT_JSON = "The model did not return valid JSON";
    static final String REASON_NOT_AN_OBJECT = "The model did not return a JSON object";
    static final String REASON_MISSING_FIELD = "The model omitted a required field";
    static final String REASON_BAD_SUMMARY = "The model returned no usable summary";
    static final String REASON_NOT_AN_ARRAY = "The model returned a malformed list";
    static final String REASON_BAD_LIST_ENTRY = "The model returned a malformed list entry";

    private final AiAnalysisProperties properties;

    public AnalysisJsonParser(AiAnalysisProperties properties) {
        this.properties = properties;
    }

    public AnalysisContent parse(String reply) {
        if (reply == null || reply.isBlank()) {
            throw reject(REASON_EMPTY);
        }

        JsonNode root = readObject(stripFence(reply.strip()));

        return new AnalysisContent(
                summary(root),
                list(root, FIELD_PARTIES),
                list(root, FIELD_IMPORTANT_DATES),
                list(root, FIELD_OBLIGATIONS),
                list(root, FIELD_KEY_CLAUSES),
                list(root, FIELD_RISKS));
    }

    // ------------------------------------------------------------- parsing

    /**
     * Removes ONE surrounding markdown fence, if the reply is entirely wrapped
     * in one.
     *
     * Both ends must be present. A reply that merely STARTS with a fence is
     * left alone and will fail to parse, which is correct: a half-open fence
     * means the model was writing prose, not wrapping an object.
     */
    private static String stripFence(String text) {
        if (!text.startsWith(FENCE) || !text.endsWith(FENCE) || text.length() <= 2 * FENCE.length()) {
            return text;
        }

        // Drop the opening fence and its language tag ("```json"), which runs to
        // the end of that line.
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
            /*
             * The cause is attached for the stack trace but its MESSAGE is never
             * used: Jackson quotes the offending source text, which here is the
             * model's reply, which is the user's document paraphrased.
             */
            log.warn("Analysis reply was not parseable JSON ({} characters)", json.length());
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
            // A blank summary is a non-answer wearing the shape of an answer.
            throw reject(REASON_BAD_SUMMARY);
        }

        return clip(summary, properties.maxSummaryCharacters());
    }

    /**
     * One list field: present, an array, and every entry a non-blank string.
     *
     * NON-TEXTUAL ENTRIES ARE REJECTED, NOT COERCED. {@code node.asText()} on an
     * object entry yields the literal JSON - a "party" reading
     * {@code {"name":"X"}} - and on a number it yields a bare figure with no
     * indication of what it counts. Coercion is exactly how nonsense gets
     * presented as a finding.
     *
     * BLANK ENTRIES ARE DROPPED, and that is a different case rather than an
     * inconsistency: an empty string is not a malformed value, it is the absence
     * of one, so removing it changes nothing about what the model claimed.
     */
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
                /*
                 * STOP AT THE CEILING, keeping the model's own order. A model
                 * that pads a list pads the tail, so the front is the part worth
                 * keeping - and taking a prefix is deterministic, where any
                 * selection rule would be a judgement this class is not
                 * qualified to make.
                 */
                log.debug("Analysis field '{}' truncated at {} entries", field, entries.size());
                break;
            }
        }

        return List.copyOf(entries);
    }

    /**
     * The key must be PRESENT. A JSON null counts as absent, because "null"
     * and "the key is not there" mean the same thing - the model declined to
     * answer this category - and the prompt asks for [] when the answer is
     * genuinely nothing.
     */
    private JsonNode required(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            log.warn("Analysis reply omitted required field '{}'", field);
            throw reject(REASON_MISSING_FIELD);
        }
        return node;
    }

    // ------------------------------------------------------------- helpers

    /** Bounds one string. Truncation keeps the front, which is the informative end. */
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
