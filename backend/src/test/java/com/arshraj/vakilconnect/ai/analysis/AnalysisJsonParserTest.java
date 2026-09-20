package com.arshraj.vakilconnect.ai.analysis;

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
 * The strict parser: what it accepts, and everything it refuses.
 *
 * THESE ARE THE TESTS THAT MATTER MOST IN AI-4. Everything else in the feature
 * is plumbing around one dangerous step - taking text a language model produced
 * from a document a stranger uploaded and turning it into a typed response
 * body. The rule the whole design rests on is that the parser reads six named
 * CONTENT fields and nothing else, so there is no route from model output to
 * document identity.
 */
@DisplayName("AnalysisJsonParser")
class AnalysisJsonParserTest {

    private static final int MAX_SUMMARY = 2000;
    private static final int MAX_ITEMS = 20;
    private static final int MAX_ITEM_CHARS = 400;

    private final AnalysisJsonParser parser = new AnalysisJsonParser(
            new AiAnalysisProperties(12000, MAX_SUMMARY, MAX_ITEMS, MAX_ITEM_CHARS));

    private AiAnswerUnavailableException rejects(String reply) {
        return assertThrows(AiAnswerUnavailableException.class, () -> parser.parse(reply));
    }

    // --------------------------------------------------------- happy path

    @Test
    @DisplayName("a well-formed reply parses into every field")
    void parsesAWellFormedReply() {
        AnalysisContent content = parser.parse(AnalysisFixtures.validReply());

        assertEquals("A residential tenancy agreement between two parties.", content.summary());
        assertEquals(2, content.parties().size());
        assertTrue(content.parties().contains("Landlord: Ramesh Kumar"));
        assertEquals(1, content.importantDates().size());
        assertEquals(1, content.obligations().size());
        assertEquals(1, content.keyClauses().size());
        assertEquals(1, content.risks().size());
    }

    @Test
    @DisplayName("EMPTY ARRAYS are a legitimate answer, not a failure")
    void emptyListsAreAccepted() {
        /*
         * The distinction the whole contract turns on. [] means "the document
         * states none of these", which is a finding; an ABSENT key means the
         * model did not answer, which is not. Accepting the first and rejecting
         * the second is what keeps them from collapsing into each other.
         */
        AnalysisContent content = parser.parse(AnalysisFixtures.emptyListsReply());

        assertTrue(content.parties().isEmpty());
        assertTrue(content.risks().isEmpty());
        assertFalse(content.summary().isBlank());
    }

    // ------------------------------------------- the identity guarantee

    @Test
    @DisplayName("THE MODEL CANNOT SUPPLY documentId, documentName OR ANY OTHER FIELD")
    void modelSuppliedIdentityIsNotRead() {
        /*
         * THE SINGLE MOST IMPORTANT TEST IN THIS CLASS.
         *
         * The reply carries documentId, documentName, userId and grantedAccess -
         * all syntactically valid, all exactly what an injected document would
         * ask the model to emit. The assertion is structural rather than
         * behavioural: AnalysisContent has SIX components and none of them is an
         * identifier, so the values have nowhere to go. A future refactor that
         * added a documentId component to AnalysisContent would break this file
         * at COMPILE time, which is the strongest form this guarantee can take.
         */
        AnalysisContent content = parser.parse(AnalysisFixtures.replyClaimingIdentity());

        assertEquals(6, AnalysisContent.class.getRecordComponents().length,
                "AnalysisContent must never gain an identity component");

        String rendered = content.summary() + content.parties() + content.importantDates()
                + content.obligations() + content.keyClauses() + content.risks();

        assertFalse(rendered.contains(AnalysisFixtures.FORGED_DOCUMENT_ID.toString()),
                "a model-supplied document id reached the parsed content");
        assertFalse(rendered.contains("attacker-owned.pdf"),
                "a model-supplied document name reached the parsed content");
        assertFalse(rendered.contains("grantedAccess"),
                "an unknown model field reached the parsed content");
    }

    // ----------------------------------------------------- malformed JSON

    @Test
    @DisplayName("malformed JSON is refused, never salvaged")
    void malformedJsonIsRejected() {
        // A plain loop rather than @ParameterizedTest: nothing else in this
        // suite uses junit-jupiter-params, and one assertion style across the
        // build is worth more than the nicer failure names.
        List<String> malformed = List.of(
                "not json at all",
                "{",
                "{\"summary\": }",
                "{'summary': 'single quotes are not JSON'}",
                "{\"summary\": \"unterminated");

        for (String reply : malformed) {
            AiAnswerUnavailableException e = rejects(reply);
            assertEquals(AnalysisJsonParser.REASON_NOT_JSON, e.getMessage(),
                    "wrong rejection reason for: " + reply);
        }
    }

    @Test
    @DisplayName("TRAILING TEXT after the object is refused")
    void trailingTokensAreRejected() {
        /*
         * The failure this codebase has already made once. Jackson's
         * FAIL_ON_TRAILING_TOKENS is OFF by default, so without it enabled the
         * parser reads the object, ignores everything after it, and reports
         * success - which is exactly how Ollama's NDJSON stream looked like a
         * one-token completion in AI-0.
         *
         * "Sure, here's the analysis" BEFORE the object is caught by the same
         * strictness from the other side: the reply must BE the object.
         */
        rejects(AnalysisFixtures.validReply() + "\n\nI hope that helps!");
        rejects("Sure! Here is the analysis:\n" + AnalysisFixtures.validReply());
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

    // ------------------------------------------------------- code fences

    @Test
    @DisplayName("ONE surrounding markdown fence is stripped - the single concession")
    void surroundingCodeFenceIsStripped() {
        /*
         * Small local models wrap JSON in ```json even when told not to, and
         * failing every analysis over it would make the feature unusable with
         * exactly the models it is built for. This is safe where brace-hunting
         * is not: a fence at both ends is self-delimiting, so removing it cannot
         * change WHICH bytes are treated as the object.
         */
        AnalysisContent content =
                parser.parse("```json\n" + AnalysisFixtures.validReply() + "\n```");

        assertEquals(2, content.parties().size());
        assertEquals(2, parser.parse("```\n" + AnalysisFixtures.validReply() + "\n```")
                .parties().size(), "a fence with no language tag works too");
    }

    @Test
    @DisplayName("a HALF-OPEN fence is not repaired")
    void halfOpenFenceIsRejected() {
        // A reply that opens a fence and never closes it was writing prose, not
        // wrapping an object. Guessing where the object ends is the brace-hunt
        // this parser refuses to do.
        rejects("```json\n" + AnalysisFixtures.validReply());
    }

    // -------------------------------------------------- missing required

    @Test
    @DisplayName("EVERY required key must be present - absence is not emptiness")
    void missingFieldsAreRejected() {
        /*
         * Defaulting an absent list to [] would silently convert "the model did
         * not answer" into "the document contains none of these" - and for
         * `risks` on a legal platform that is precisely the wrong direction to
         * guess in.
         */
        List<String> required = List.of(AnalysisJsonParser.FIELD_SUMMARY,
                AnalysisJsonParser.FIELD_PARTIES, AnalysisJsonParser.FIELD_IMPORTANT_DATES,
                AnalysisJsonParser.FIELD_OBLIGATIONS, AnalysisJsonParser.FIELD_KEY_CLAUSES,
                AnalysisJsonParser.FIELD_RISKS);

        for (String field : required) {
            AiAnswerUnavailableException e = rejects(AnalysisFixtures.replyWithout(field));
            assertEquals(AnalysisJsonParser.REASON_MISSING_FIELD, e.getMessage(),
                    "omitting \"" + field + "\" must be rejected as a missing field");
        }
    }

    @Test
    @DisplayName("an explicit null counts as absent")
    void nullFieldsAreRejected() {
        for (String field : List.of(AnalysisJsonParser.FIELD_SUMMARY,
                AnalysisJsonParser.FIELD_PARTIES, AnalysisJsonParser.FIELD_RISKS)) {

            AiAnswerUnavailableException e = rejects(AnalysisFixtures.replyWithNull(field));
            assertEquals(AnalysisJsonParser.REASON_MISSING_FIELD, e.getMessage(),
                    "\"" + field + "\": null must be treated as absent");
        }
    }

    // ---------------------------------------------------- field contracts

    @Test
    @DisplayName("a blank or non-textual summary is refused")
    void badSummaryIsRejected() {
        rejects(AnalysisFixtures.replyWith("summary", new TextNode("   ")));
        rejects(AnalysisFixtures.replyWith("summary", new IntNode(7)));
        rejects(AnalysisFixtures.replyWith("summary",
                AnalysisFixtures.MAPPER.createArrayNode().add("a summary in a list")));
    }

    @Test
    @DisplayName("a list field that is not an array is refused")
    void nonArrayListIsRejected() {
        rejects(AnalysisFixtures.replyWith("parties", new TextNode("Ramesh and Anita")));
        rejects(AnalysisFixtures.replyWith("parties",
                AnalysisFixtures.MAPPER.createObjectNode().put("name", "Ramesh")));
    }

    @Test
    @DisplayName("a non-textual list ENTRY is refused, never coerced")
    void nonTextualEntriesAreRejected() {
        /*
         * asText() on an object yields the literal JSON, so coercion would put
         * {"name":"Ramesh"} in front of a user as the name of a party. Rejecting
         * is the honest outcome: the model did not follow the schema, and
         * presenting its output anyway is how nonsense becomes a finding.
         */
        rejects(AnalysisFixtures.replyWith("parties",
                AnalysisFixtures.MAPPER.createArrayNode()
                        .add(AnalysisFixtures.MAPPER.createObjectNode().put("name", "Ramesh"))));

        rejects(AnalysisFixtures.replyWith("importantDates",
                AnalysisFixtures.MAPPER.createArrayNode().add(2026)));
    }

    @Test
    @DisplayName("BLANK entries are dropped, which is different from being malformed")
    void blankEntriesAreDropped() {
        // An empty string is not a wrong value, it is the absence of one, so
        // removing it changes nothing the model actually claimed.
        AnalysisContent content = parser.parse(AnalysisFixtures.replyWith("parties",
                AnalysisFixtures.MAPPER.createArrayNode()
                        .add("Landlord: Ramesh Kumar").add("").add("   ")));

        assertEquals(1, content.parties().size());
        assertEquals("Landlord: Ramesh Kumar", content.parties().get(0));
    }

    // ------------------------------------------------------------ bounding

    @Test
    @DisplayName("a runaway list is CLIPPED to the ceiling, keeping the model's order")
    void listsAreBounded() {
        /*
         * Small models loop on lists. Without a ceiling one degenerate
         * generation becomes the response body, so this drops the tail - which
         * is where padding accumulates - and keeps the front, deterministically.
         */
        var many = AnalysisFixtures.MAPPER.createArrayNode();
        IntStream.range(0, MAX_ITEMS * 5).forEach(i -> many.add("obligation " + i));

        AnalysisContent content =
                parser.parse(AnalysisFixtures.replyWith("obligations", many));

        assertEquals(MAX_ITEMS, content.obligations().size());
        assertEquals("obligation 0", content.obligations().get(0),
                "the clip must keep the model's own order from the front");
    }

    @Test
    @DisplayName("an over-long entry is TRUNCATED rather than dropped")
    void longEntriesAreTruncated() {
        // A clipped obligation still tells the user something; an absent one
        // does not. The opposite trade from the list ceiling, on purpose.
        AnalysisContent content = parser.parse(AnalysisFixtures.replyWith("obligations",
                AnalysisFixtures.MAPPER.createArrayNode().add("x".repeat(MAX_ITEM_CHARS * 3))));

        assertEquals(1, content.obligations().size());
        assertTrue(content.obligations().get(0).length() <= MAX_ITEM_CHARS + 1,
                "entry was not clipped to the configured ceiling");
    }

    @Test
    @DisplayName("an over-long summary is truncated")
    void longSummaryIsTruncated() {
        AnalysisContent content = parser.parse(AnalysisFixtures.replyWith(
                "summary", new TextNode("y".repeat(MAX_SUMMARY * 2))));

        assertTrue(content.summary().length() <= MAX_SUMMARY + 1);
    }

    // ------------------------------------------------------------- safety

    @Test
    @DisplayName("no rejection message ever carries the model's text")
    void rejectionMessagesCarryNoContent() {
        /*
         * The reply is the user's document paraphrased. Jackson's own parse
         * errors quote the offending source, so letting the cause's message
         * become the exception message would route document content into logs
         * and, through GlobalExceptionHandler, very nearly into a response body.
         */
        String secret = "CONFIDENTIAL SETTLEMENT AMOUNT RS 4,50,000";

        AiAnswerUnavailableException e = rejects("{\"summary\": \"" + secret + "\" ");

        assertFalse(String.valueOf(e.getMessage()).contains(secret),
                "the model's text leaked into the rejection message");
        assertEquals(AnalysisJsonParser.REASON_NOT_JSON, e.getMessage());
    }

    @Test
    @DisplayName("AnalysisContent.toString() does not print the analysis")
    void contentToStringIsSafe() {
        String rendered = parser.parse(AnalysisFixtures.validReply()).toString();

        assertFalse(rendered.contains("Ramesh"), "a party name leaked through toString()");
        assertFalse(rendered.contains("residential tenancy"),
                "the summary leaked through toString()");
        assertTrue(rendered.contains("<not shown>"));
    }
}
