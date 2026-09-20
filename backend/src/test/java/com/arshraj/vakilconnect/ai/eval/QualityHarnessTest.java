package com.arshraj.vakilconnect.ai.eval;

import com.arshraj.vakilconnect.ai.analysis.AnalysisQualityCases;
import com.arshraj.vakilconnect.ai.compare.ComparisonQualityCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI-6: the quality harness itself.
 *
 * WHAT THIS ADDS THAT AnalysisJsonParserTest / ComparisonJsonParserTest DO NOT
 * ALREADY GIVE. Those classes are exhaustive for the ONE parser they cover,
 * and stay exactly as useful as before this phase - a failure here does not
 * replace reading them, it points at them. What this test adds is a check
 * ACROSS parsers: every parser registered here must cover every category in
 * {@link QualityCategory}, not just the categories its author happened to
 * think of. A future AI-N that adds a third structured-JSON parser and
 * forgets to test, say, that a malformed reply is rejected will fail THIS
 * test the moment its EvalCase list is registered below with a gap - before
 * anyone notices in production that the new parser silently accepts garbage.
 *
 * REGISTERING A NEW PARSER IS A ONE-LINE ADDITION TO {@code REGISTERED}
 * below, by design: the cost of adding coverage should never be an excuse to
 * skip it.
 *
 * ZERO PAID CALLS, ZERO NETWORK. Every EvalCase runs a real parser instance
 * (the same classes production uses) against a fixed, in-memory string reply
 * - there is no LlmClient here to fail or cost anything, deliberately, since
 * a quality gate that depends on live inference would be flaky by
 * construction and impossible to run in CI without a model.
 */
class QualityHarnessTest {

    /** Every parser this harness knows about. Add a line here for a new one. */
    private static final List<EvalCase> REGISTERED = combine(
            AnalysisQualityCases.cases(),
            ComparisonQualityCases.cases());

    @SafeVarargs
    private static List<EvalCase> combine(List<EvalCase>... groups) {
        List<EvalCase> all = new ArrayList<>();
        for (List<EvalCase> group : groups) {
            all.addAll(group);
        }
        return List.copyOf(all);
    }

    @Test
    @DisplayName("every registered parser passes every one of its quality checks")
    void allChecksPass() {
        EvalReport report = EvalReport.run(REGISTERED);

        assertTrue(report.failures().isEmpty(), report.summary());
    }

    @Test
    @DisplayName("every registered parser covers every required quality category")
    void everyParserCoversEveryCategory() {
        EvalReport report = EvalReport.run(REGISTERED);
        Set<QualityCategory> required = EnumSet.allOf(QualityCategory.class);

        for (String parser : report.parsers()) {
            Set<QualityCategory> covered = report.categoriesCovered(parser);

            assertTrue(covered.containsAll(required),
                    parser + " is missing coverage for: "
                            + missing(required, covered));
        }
    }

    @Test
    @DisplayName("at least one parser is registered - an empty harness proves nothing")
    void atLeastOneParserIsRegistered() {
        EvalReport report = EvalReport.run(REGISTERED);

        assertTrue(report.parsers().size() >= 2,
                "expected AnalysisJsonParser and ComparisonJsonParser to both be registered");
    }

    private static Set<QualityCategory> missing(Set<QualityCategory> required, Set<QualityCategory> covered) {
        EnumSet<QualityCategory> gap = EnumSet.copyOf(required);
        gap.removeAll(covered);
        return gap;
    }
}
