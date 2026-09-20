package com.arshraj.vakilconnect.ai.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The harness's own machinery, tested independently of any real parser -
 * these prove EvalReport's bookkeeping is correct using synthetic cases
 * whose pass/fail outcome is controlled directly, rather than depending on
 * AnalysisJsonParser or ComparisonJsonParser actually being right.
 */
@DisplayName("EvalReport")
class EvalReportTest {

    private static EvalCase passing(String parser, QualityCategory category) {
        return new EvalCase(parser, category, "always passes", () -> {
        });
    }

    private static EvalCase failing(String parser, QualityCategory category) {
        return new EvalCase(parser, category, "always fails", () -> {
            throw new AssertionError("deliberate failure for testing");
        });
    }

    @Test
    @DisplayName("a report with only passing cases has zero failures")
    void allPassingCasesYieldNoFailures() {
        EvalReport report = EvalReport.run(List.of(
                passing("FakeParser", QualityCategory.VALID_PARSE),
                passing("FakeParser", QualityCategory.MALFORMED_REJECTED)));

        assertEquals(2, report.total());
        assertEquals(2, report.passedCount());
        assertTrue(report.failures().isEmpty());
    }

    @Test
    @DisplayName("A FAILING CASE IS RECORDED, NOT SILENTLY DROPPED OR RETHROWN")
    void aFailingCaseIsCaughtAndRecorded() {
        // THE CENTRAL PROPERTY OF THIS CLASS: EvalReport.run() must never let
        // one case's exception propagate out and abort the batch - the whole
        // point of a report is to see every result in one pass.
        EvalReport report = EvalReport.run(List.of(
                passing("FakeParser", QualityCategory.VALID_PARSE),
                failing("FakeParser", QualityCategory.MALFORMED_REJECTED),
                passing("FakeParser", QualityCategory.EMPTY_LIST_ACCEPTED)));

        assertEquals(3, report.total());
        assertEquals(2, report.passedCount());
        assertEquals(1, report.failures().size());
        assertEquals(QualityCategory.MALFORMED_REJECTED,
                report.failures().get(0).evalCase().category());
    }

    @Test
    @DisplayName("failureDetail never carries anything beyond the harness's own assertion text")
    void failureDetailIsBoundedToOwnAssertions() {
        EvalReport report = EvalReport.run(List.of(failing("FakeParser", QualityCategory.VALID_PARSE)));

        String detail = report.failures().get(0).failureDetail();
        assertTrue(detail.contains("deliberate failure for testing"));
    }

    @Test
    @DisplayName("categoriesCovered reflects exactly the categories registered per parser")
    void categoriesCoveredIsPerParser() {
        EvalReport report = EvalReport.run(List.of(
                passing("ParserA", QualityCategory.VALID_PARSE),
                passing("ParserA", QualityCategory.LIST_BOUNDED),
                passing("ParserB", QualityCategory.MALFORMED_REJECTED)));

        Set<QualityCategory> coveredA = report.categoriesCovered("ParserA");
        assertTrue(coveredA.contains(QualityCategory.VALID_PARSE));
        assertTrue(coveredA.contains(QualityCategory.LIST_BOUNDED));
        assertFalse(coveredA.contains(QualityCategory.MALFORMED_REJECTED));

        assertEquals(Set.of(QualityCategory.MALFORMED_REJECTED), report.categoriesCovered("ParserB"));
    }

    @Test
    @DisplayName("a category with no registered case for a parser is simply absent, not a crash")
    void uncoveredCategoryIsAbsentNotAnError() {
        EvalReport report = EvalReport.run(List.of(passing("ParserA", QualityCategory.VALID_PARSE)));

        assertFalse(report.categoriesCovered("ParserA").contains(QualityCategory.NO_CONTENT_LEAK));
        assertTrue(report.categoriesCovered("NeverRegisteredParser").isEmpty());
    }

    @Test
    @DisplayName("parsers() lists every distinct parser name that registered a case")
    void parsersListsDistinctNames() {
        EvalReport report = EvalReport.run(List.of(
                passing("ParserA", QualityCategory.VALID_PARSE),
                passing("ParserA", QualityCategory.LIST_BOUNDED),
                passing("ParserB", QualityCategory.VALID_PARSE)));

        assertEquals(Set.of("ParserA", "ParserB"), report.parsers());
    }

    @Test
    @DisplayName("summary() is plain text naming the parser, category and description - never model content")
    void summaryIsHumanReadableAndBounded() {
        EvalReport report = EvalReport.run(List.of(
                failing("FakeParser", QualityCategory.MALFORMED_REJECTED)));

        String summary = report.summary();
        assertTrue(summary.contains("FakeParser"));
        assertTrue(summary.contains("MALFORMED_REJECTED"));
        assertTrue(summary.contains("always fails"));
        assertTrue(summary.contains("0/1"));
    }

    @Test
    @DisplayName("an empty case list produces an empty, non-failing report")
    void emptyCaseListIsValid() {
        EvalReport report = EvalReport.run(List.of());

        assertEquals(0, report.total());
        assertTrue(report.failures().isEmpty());
        assertTrue(report.parsers().isEmpty());
    }
}
