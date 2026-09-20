package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.ai.eval.EvalCase;
import com.arshraj.vakilconnect.ai.eval.QualityCategory;
import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI-6's registration of AI-5's {@link ComparisonJsonParser} into the quality
 * harness. Mirrors {@code AnalysisQualityCases} exactly - same eight
 * categories, same reuse-the-fixtures approach - except {@code IDENTITY_FORGERY_CONTAINED}
 * checks a FOUR-component record instead of six, since comparison identity
 * spans two documents rather than one.
 */
public final class ComparisonQualityCases {

    private static final String PARSER = "ComparisonJsonParser";
    private static final int MAX_SUMMARY = 2000;
    private static final int MAX_ITEMS = 20;
    private static final int MAX_ITEM_CHARS = 400;

    private static final ComparisonJsonParser PARSER_INSTANCE = new ComparisonJsonParser(
            new AiComparisonProperties(6000, MAX_SUMMARY, MAX_ITEMS, MAX_ITEM_CHARS));

    public static List<EvalCase> cases() {
        return List.of(
                new EvalCase(PARSER, QualityCategory.VALID_PARSE,
                        "a well-formed reply parses into every field",
                        ComparisonQualityCases::validParse),

                new EvalCase(PARSER, QualityCategory.EMPTY_LIST_ACCEPTED,
                        "empty lists are accepted - the documents may not differ",
                        ComparisonQualityCases::emptyListsAccepted),

                new EvalCase(PARSER, QualityCategory.MALFORMED_REJECTED,
                        "malformed JSON is refused, never salvaged",
                        ComparisonQualityCases::malformedRejected),

                new EvalCase(PARSER, QualityCategory.MISSING_FIELD_REJECTED,
                        "a required field that is absent is refused",
                        ComparisonQualityCases::missingFieldRejected),

                new EvalCase(PARSER, QualityCategory.IDENTITY_FORGERY_CONTAINED,
                        "the model cannot supply either document's identity",
                        ComparisonQualityCases::identityForgeryContained),

                new EvalCase(PARSER, QualityCategory.LIST_BOUNDED,
                        "a runaway list is clipped to the configured ceiling",
                        ComparisonQualityCases::listBounded),

                new EvalCase(PARSER, QualityCategory.ENTRY_TRUNCATED,
                        "an over-long entry is truncated rather than dropped",
                        ComparisonQualityCases::entryTruncated),

                new EvalCase(PARSER, QualityCategory.NO_CONTENT_LEAK,
                        "no rejection message ever carries the model's text",
                        ComparisonQualityCases::noContentLeak));
    }

    private static void validParse() {
        ComparisonContent content = PARSER_INSTANCE.parse(ComparisonFixtures.validReply());
        assertFalse(content.summary().isBlank());
        assertEquals(1, content.keyDifferences().size());
    }

    private static void emptyListsAccepted() {
        ComparisonContent content = PARSER_INSTANCE.parse(ComparisonFixtures.emptyListsReply());
        assertTrue(content.keyDifferences().isEmpty());
        assertFalse(content.summary().isBlank());
    }

    private static void malformedRejected() {
        AiAnswerUnavailableException e = assertThrows(AiAnswerUnavailableException.class,
                () -> PARSER_INSTANCE.parse("not json at all"));
        assertEquals(ComparisonJsonParser.REASON_NOT_JSON, e.getMessage());
    }

    private static void missingFieldRejected() {
        AiAnswerUnavailableException e = assertThrows(AiAnswerUnavailableException.class,
                () -> PARSER_INSTANCE.parse(ComparisonFixtures.replyWithout(
                        ComparisonJsonParser.FIELD_SUMMARY)));
        assertEquals(ComparisonJsonParser.REASON_MISSING_FIELD, e.getMessage());
    }

    private static void identityForgeryContained() {
        ComparisonContent content = PARSER_INSTANCE.parse(ComparisonFixtures.replyClaimingIdentity());

        assertEquals(4, ComparisonContent.class.getRecordComponents().length,
                "ComparisonContent must never gain an identity component");

        String rendered = content.summary() + content.keyDifferences()
                + content.onlyInFirst() + content.onlyInSecond();
        assertFalse(rendered.contains(ComparisonFixtures.FORGED_ID.toString()));
        assertFalse(rendered.contains("attacker-owned.pdf"));
    }

    private static void listBounded() {
        ObjectMapper mapper = ComparisonFixtures.MAPPER;
        var many = mapper.createArrayNode();
        IntStream.range(0, MAX_ITEMS * 5).forEach(i -> many.add("difference " + i));

        ComparisonContent content = PARSER_INSTANCE.parse(
                ComparisonFixtures.replyWith(ComparisonJsonParser.FIELD_KEY_DIFFERENCES, many));

        assertEquals(MAX_ITEMS, content.keyDifferences().size());
        assertEquals("difference 0", content.keyDifferences().get(0));
    }

    private static void entryTruncated() {
        ObjectMapper mapper = ComparisonFixtures.MAPPER;
        ComparisonContent content = PARSER_INSTANCE.parse(ComparisonFixtures.replyWith(
                ComparisonJsonParser.FIELD_KEY_DIFFERENCES,
                mapper.createArrayNode().add("x".repeat(MAX_ITEM_CHARS * 3))));

        assertEquals(1, content.keyDifferences().size());
        assertTrue(content.keyDifferences().get(0).length() <= MAX_ITEM_CHARS + 1);
    }

    private static void noContentLeak() {
        String secret = "CONFIDENTIAL RENT FIGURE RS 4,50,000";
        AiAnswerUnavailableException e = assertThrows(AiAnswerUnavailableException.class,
                () -> PARSER_INSTANCE.parse("{\"summary\": \"" + secret + "\" "));

        assertFalse(String.valueOf(e.getMessage()).contains(secret));
        assertEquals(ComparisonJsonParser.REASON_NOT_JSON, e.getMessage());
    }

    private ComparisonQualityCases() {
    }
}
