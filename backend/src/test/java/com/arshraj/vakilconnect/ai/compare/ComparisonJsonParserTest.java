package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strict comparison parser. Mirrors AI-4's AnalysisJsonParserTest - see
 * that class for why these specific properties (never salvage JSON from
 * prose, absence is not emptiness, no coercion of non-textual entries) are
 * what this suite checks rather than "does it work on a good example".
 */
@DisplayName("ComparisonJsonParser")
class ComparisonJsonParserTest {

    private static final int MAX_SUMMARY = 2000;
    private static final int MAX_ITEMS = 20;
    private static final int MAX_ITEM_CHARS = 400;

    private final ComparisonJsonParser parser = new ComparisonJsonParser(
            new AiComparisonProperties(6000, MAX_SUMMARY, MAX_ITEMS, MAX_ITEM_CHARS));

    private AiAnswerUnavailableException rejects(String reply) {
        return assertThrows(AiAnswerUnavailableException.class, () -> parser.parse(reply));
    }

    @Test
    @DisplayName("a well-formed reply parses into every field")
    void parsesAWellFormedReply() {
        ComparisonContent content = parser.parse(ComparisonFixtures.validReply());

        assertFalse(content.summary().isBlank());
        assertEquals(1, content.keyDifferences().size());
        assertEquals(1, content.onlyInFirst().size());
        assertEquals(1, content.onlyInSecond().size());
    }

    @Test
    @DisplayName("EMPTY ARRAYS are a legitimate answer - the documents may not differ")
    void emptyListsAreAccepted() {
        ComparisonContent content = parser.parse(ComparisonFixtures.emptyListsReply());

        assertTrue(content.keyDifferences().isEmpty());
        assertTrue(content.onlyInFirst().isEmpty());
        assertTrue(content.onlyInSecond().isEmpty());
        assertFalse(content.summary().isBlank());
    }

    @Test
    @DisplayName("THE MODEL CANNOT SUPPLY EITHER DOCUMENT'S IDENTITY")
    void modelSuppliedIdentityIsNotRead() {
        /*
         * THE SINGLE MOST IMPORTANT TEST IN THIS CLASS, mirroring AI-4's. The
         * reply carries firstDocumentId, firstDocumentName and grantedAccess -
         * all syntactically valid, all exactly what an injected document would
         * ask the model to emit. ComparisonContent has FOUR components and
         * none of them is an identifier, so the values have nowhere to go.
         */
        ComparisonContent content = parser.parse(ComparisonFixtures.replyClaimingIdentity());

        assertEquals(4, ComparisonContent.class.getRecordComponents().length,
                "ComparisonContent must never gain an identity component");

        String rendered = content.summary() + content.keyDifferences()
                + content.onlyInFirst() + content.onlyInSecond();

        assertFalse(rendered.contains(ComparisonFixtures.FORGED_ID.toString()),
                "a model-supplied document id reached the parsed content");
        assertFalse(rendered.contains("attacker-owned.pdf"));
        assertFalse(rendered.contains("grantedAccess"));
    }

    @Test
    @DisplayName("malformed JSON is refused, never salvaged")
    void malformedJsonIsRejected() {
        for (String reply : List.of(
                "not json at all", "{", "{\"summary\": }",
                "{'summary': 'single quotes are not JSON'}",
                "{\"summary\": \"unterminated")) {

            AiAnswerUnavailableException e = rejects(reply);
            assertEquals(ComparisonJsonParser.REASON_NOT_JSON, e.getMessage(),
                    "wrong rejection reason for: " + reply);
        }
    }

    @Test
    @DisplayName("TRAILING TEXT after the object is refused")
    void trailingTokensAreRejected() {
        // FAIL_ON_TRAILING_TOKENS - the exact default gap that let Ollama's
        // NDJSON stream look like a one-token completion in AI-0.
        rejects(ComparisonFixtures.validReply() + "\n\nHope that helps!");
        rejects("Sure! Here is the comparison:\n" + ComparisonFixtures.validReply());
    }

    @Test
    @DisplayName("a JSON value that is not an object is refused")
    void nonObjectRepliesAreRejected() {
        rejects("[]");
        rejects("\"just a string\"");
        rejects("42");
        rejects("null");
    }

    @Test
    @DisplayName("empty and blank replies are refused")
    void emptyRepliesAreRejected() {
        rejects(null);
        rejects("");
        rejects("   \n\t  ");
    }

    @Test
    @DisplayName("ONE surrounding markdown fence is stripped - the single concession")
    void surroundingCodeFenceIsStripped() {
        ComparisonContent content =
                parser.parse("```json\n" + ComparisonFixtures.validReply() + "\n```");

        assertEquals(1, content.keyDifferences().size());
    }

    @Test
    @DisplayName("a HALF-OPEN fence is not repaired")
    void halfOpenFenceIsRejected() {
        rejects("```json\n" + ComparisonFixtures.validReply());
    }

    @Test
    @DisplayName("EVERY required key must be present - absence is not emptiness")
    void missingFieldsAreRejected() {
        List<String> required = List.of(ComparisonJsonParser.FIELD_SUMMARY,
                ComparisonJsonParser.FIELD_KEY_DIFFERENCES,
                ComparisonJsonParser.FIELD_ONLY_IN_FIRST,
                ComparisonJsonParser.FIELD_ONLY_IN_SECOND);

        for (String field : required) {
            AiAnswerUnavailableException e = rejects(ComparisonFixtures.replyWithout(field));
            assertEquals(ComparisonJsonParser.REASON_MISSING_FIELD, e.getMessage(),
                    "omitting \"" + field + "\" must be rejected as a missing field");
        }
    }

    @Test
    @DisplayName("an explicit null counts as absent")
    void nullFieldsAreRejected() {
        for (String field : List.of(ComparisonJsonParser.FIELD_SUMMARY,
                ComparisonJsonParser.FIELD_KEY_DIFFERENCES)) {

            AiAnswerUnavailableException e = rejects(ComparisonFixtures.replyWithNull(field));
            assertEquals(ComparisonJsonParser.REASON_MISSING_FIELD, e.getMessage());
        }
    }

    @Test
    @DisplayName("a blank or non-textual summary is refused")
    void badSummaryIsRejected() {
        rejects(ComparisonFixtures.replyWith(ComparisonJsonParser.FIELD_SUMMARY, new TextNode("   ")));
        rejects(ComparisonFixtures.replyWith(ComparisonJsonParser.FIELD_SUMMARY, new IntNode(7)));
    }

    @Test
    @DisplayName("a list field that is not an array is refused")
    void nonArrayListIsRejected() {
        rejects(ComparisonFixtures.replyWith(ComparisonJsonParser.FIELD_KEY_DIFFERENCES,
                new TextNode("rent differs")));
    }

    @Test
    @DisplayName("a non-textual list ENTRY is refused, never coerced")
    void nonTextualEntriesAreRejected() {
        rejects(ComparisonFixtures.replyWith(ComparisonJsonParser.FIELD_ONLY_IN_FIRST,
                ComparisonFixtures.MAPPER.createArrayNode()
                        .add(ComparisonFixtures.MAPPER.createObjectNode().put("term", "grace period"))));
    }

    @Test
    @DisplayName("BLANK entries are dropped, which is different from being malformed")
    void blankEntriesAreDropped() {
        ComparisonContent content = parser.parse(ComparisonFixtures.replyWith(
                ComparisonJsonParser.FIELD_KEY_DIFFERENCES,
                ComparisonFixtures.MAPPER.createArrayNode().add("rent differs").add("").add("  ")));

        assertEquals(1, content.keyDifferences().size());
        assertEquals("rent differs", content.keyDifferences().get(0));
    }

    @Test
    @DisplayName("a runaway list is CLIPPED to the ceiling, keeping the model's order")
    void listsAreBounded() {
        var many = ComparisonFixtures.MAPPER.createArrayNode();
        IntStream.range(0, MAX_ITEMS * 5).forEach(i -> many.add("difference " + i));

        ComparisonContent content = parser.parse(
                ComparisonFixtures.replyWith(ComparisonJsonParser.FIELD_KEY_DIFFERENCES, many));

        assertEquals(MAX_ITEMS, content.keyDifferences().size());
        assertEquals("difference 0", content.keyDifferences().get(0));
    }

    @Test
    @DisplayName("an over-long entry is TRUNCATED rather than dropped")
    void longEntriesAreTruncated() {
        ComparisonContent content = parser.parse(ComparisonFixtures.replyWith(
                ComparisonJsonParser.FIELD_KEY_DIFFERENCES,
                ComparisonFixtures.MAPPER.createArrayNode().add("x".repeat(MAX_ITEM_CHARS * 3))));

        assertEquals(1, content.keyDifferences().size());
        assertTrue(content.keyDifferences().get(0).length() <= MAX_ITEM_CHARS + 1);
    }

    @Test
    @DisplayName("no rejection message ever carries the model's text")
    void rejectionMessagesCarryNoContent() {
        String secret = "CONFIDENTIAL RENT FIGURE RS 4,50,000";

        AiAnswerUnavailableException e = rejects("{\"summary\": \"" + secret + "\" ");

        assertFalse(String.valueOf(e.getMessage()).contains(secret));
        assertEquals(ComparisonJsonParser.REASON_NOT_JSON, e.getMessage());
    }

    @Test
    @DisplayName("ComparisonContent.toString() does not print the comparison")
    void contentToStringIsSafe() {
        String rendered = parser.parse(ComparisonFixtures.validReply()).toString();

        assertFalse(rendered.contains("25,000"));
        assertTrue(rendered.contains("<not shown>"));
    }
}
