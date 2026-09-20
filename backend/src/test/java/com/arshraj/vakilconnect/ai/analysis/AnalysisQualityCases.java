package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.arshraj.vakilconnect.ai.eval.EvalCase;
import com.arshraj.vakilconnect.ai.eval.QualityCategory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI-6's registration of AI-4's {@link AnalysisJsonParser} into the quality
 * harness. Reuses {@link AnalysisFixtures} rather than inventing parallel
 * fixtures - the fixtures class already IS this codebase's canonical fixed
 * set of "well-formed", "malformed", "hostile" replies, and AnalysisJsonParserTest
 * stays the place to look for a failure's full detail.
 *
 * PUBLIC (unlike the fixtures and the parser's own field constants) because
 * QualityHarnessTest lives in a neutral {@code ai.eval} package and has to
 * reach across packages to collect every parser's cases into one report.
 */
public final class AnalysisQualityCases {

    private static final String PARSER = "AnalysisJsonParser";
    private static final int MAX_SUMMARY = 2000;
    private static final int MAX_ITEMS = 20;
    private static final int MAX_ITEM_CHARS = 400;

    private static final AnalysisJsonParser PARSER_INSTANCE = new AnalysisJsonParser(
            new AiAnalysisProperties(12000, MAX_SUMMARY, MAX_ITEMS, MAX_ITEM_CHARS));

    public static List<EvalCase> cases() {
        return List.of(
                new EvalCase(PARSER, QualityCategory.VALID_PARSE,
                        "a well-formed reply parses into every field",
                        AnalysisQualityCases::validParse),

                new EvalCase(PARSER, QualityCategory.EMPTY_LIST_ACCEPTED,
                        "empty lists are accepted as a legitimate answer",
                        AnalysisQualityCases::emptyListsAccepted),

                new EvalCase(PARSER, QualityCategory.MALFORMED_REJECTED,
                        "malformed JSON is refused, never salvaged",
                        AnalysisQualityCases::malformedRejected),

                new EvalCase(PARSER, QualityCategory.MISSING_FIELD_REJECTED,
                        "a required field that is absent is refused",
                        AnalysisQualityCases::missingFieldRejected),

                new EvalCase(PARSER, QualityCategory.IDENTITY_FORGERY_CONTAINED,
                        "the model cannot supply document identity",
                        AnalysisQualityCases::identityForgeryContained),

                new EvalCase(PARSER, QualityCategory.LIST_BOUNDED,
                        "a runaway list is clipped to the configured ceiling",
                        AnalysisQualityCases::listBounded),

                new EvalCase(PARSER, QualityCategory.ENTRY_TRUNCATED,
                        "an over-long entry is truncated rather than dropped",
                        AnalysisQualityCases::entryTruncated),

                new EvalCase(PARSER, QualityCategory.NO_CONTENT_LEAK,
                        "no rejection message ever carries the model's text",
                        AnalysisQualityCases::noContentLeak));
    }

    private static void validParse() {
        AnalysisContent content = PARSER_INSTANCE.parse(AnalysisFixtures.validReply());
        assertFalse(content.summary().isBlank());
        // AnalysisFixtures.validReply() lists two parties (landlord and
        // tenant) - matches AnalysisJsonParserTest.parsesAWellFormedReply(),
        // the established contract for this fixture.
        assertEquals(2, content.parties().size());
    }

    private static void emptyListsAccepted() {
        AnalysisContent content = PARSER_INSTANCE.parse(AnalysisFixtures.emptyListsReply());
        assertTrue(content.parties().isEmpty());
        assertFalse(content.summary().isBlank());
    }

    private static void malformedRejected() {
        AiAnswerUnavailableException e = assertThrows(AiAnswerUnavailableException.class,
                () -> PARSER_INSTANCE.parse("not json at all"));
        assertEquals(AnalysisJsonParser.REASON_NOT_JSON, e.getMessage());
    }

    private static void missingFieldRejected() {
        AiAnswerUnavailableException e = assertThrows(AiAnswerUnavailableException.class,
                () -> PARSER_INSTANCE.parse(AnalysisFixtures.replyWithout(
                        AnalysisJsonParser.FIELD_SUMMARY)));
        assertEquals(AnalysisJsonParser.REASON_MISSING_FIELD, e.getMessage());
    }

    private static void identityForgeryContained() {
        AnalysisContent content = PARSER_INSTANCE.parse(AnalysisFixtures.replyClaimingIdentity());

        assertEquals(6, AnalysisContent.class.getRecordComponents().length,
                "AnalysisContent must never gain an identity component");

        String rendered = content.summary() + content.parties() + content.importantDates()
                + content.obligations() + content.keyClauses() + content.risks();
        assertFalse(rendered.contains(AnalysisFixtures.FORGED_DOCUMENT_ID.toString()));
        assertFalse(rendered.contains("attacker-owned.pdf"));
    }

    private static void listBounded() {
        ObjectMapper mapper = AnalysisFixtures.MAPPER;
        var many = mapper.createArrayNode();
        IntStream.range(0, MAX_ITEMS * 5).forEach(i -> many.add("risk " + i));

        AnalysisContent content = PARSER_INSTANCE.parse(
                AnalysisFixtures.replyWith(AnalysisJsonParser.FIELD_RISKS, many));

        assertEquals(MAX_ITEMS, content.risks().size());
        assertEquals("risk 0", content.risks().get(0));
    }

    private static void entryTruncated() {
        ObjectMapper mapper = AnalysisFixtures.MAPPER;
        AnalysisContent content = PARSER_INSTANCE.parse(AnalysisFixtures.replyWith(
                AnalysisJsonParser.FIELD_RISKS,
                mapper.createArrayNode().add("x".repeat(MAX_ITEM_CHARS * 3))));

        assertEquals(1, content.risks().size());
        assertTrue(content.risks().get(0).length() <= MAX_ITEM_CHARS + 1);
    }

    private static void noContentLeak() {
        String secret = "CONFIDENTIAL RENT FIGURE RS 4,50,000";
        AiAnswerUnavailableException e = assertThrows(AiAnswerUnavailableException.class,
                () -> PARSER_INSTANCE.parse("{\"summary\": \"" + secret + "\" "));

        assertFalse(String.valueOf(e.getMessage()).contains(secret));
        assertEquals(AnalysisJsonParser.REASON_NOT_JSON, e.getMessage());
    }

    private AnalysisQualityCases() {
    }
}
