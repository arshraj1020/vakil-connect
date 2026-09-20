package com.arshraj.vakilconnect.ai.eval;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs a batch of {@link EvalCase}s and tallies the result.
 *
 * RUNS EVERY CASE, EVEN AFTER ONE FAILS. A harness that stops at the first
 * failure would hide every other regression behind it - the whole point of
 * a quality report is to see the full extent of what broke in one pass,
 * the same reasoning that governs why the comparison service runs both of
 * its document lookups before raising either failure (see
 * DocumentComparisonServiceImpl).
 */
public final class EvalReport {

    private final List<EvalOutcome> outcomes;

    private EvalReport(List<EvalOutcome> outcomes) {
        this.outcomes = List.copyOf(outcomes);
    }

    public static EvalReport run(List<EvalCase> cases) {
        List<EvalOutcome> outcomes = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            try {
                evalCase.check().execute();
                outcomes.add(EvalOutcome.pass(evalCase));
            } catch (Throwable t) {
                outcomes.add(EvalOutcome.fail(evalCase, t));
            }
        }
        return new EvalReport(outcomes);
    }

    public List<EvalOutcome> outcomes() {
        return outcomes;
    }

    public List<EvalOutcome> failures() {
        return outcomes.stream().filter(o -> !o.passed()).toList();
    }

    public int total() {
        return outcomes.size();
    }

    public int passedCount() {
        return (int) outcomes.stream().filter(EvalOutcome::passed).count();
    }

    /** Distinct parser names that registered at least one case. */
    public Set<String> parsers() {
        return outcomes.stream().map(o -> o.evalCase().parserName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The categories a given parser registered a case for, regardless of pass/fail. */
    public Set<QualityCategory> categoriesCovered(String parserName) {
        EnumSet<QualityCategory> covered = EnumSet.noneOf(QualityCategory.class);
        outcomes.stream()
                .map(EvalOutcome::evalCase)
                .filter(c -> c.parserName().equals(parserName))
                .forEach(c -> covered.add(c.category()));
        return covered;
    }

    /**
     * A plain-text table, safe to print anywhere (stdout, CI logs, a report
     * file): every value it contains is a parser name, a category name, a
     * pass/fail flag, or an EvalOutcome failureDetail - never model text.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("AI quality harness: ").append(passedCount()).append('/').append(total())
                .append(" checks passed\n");

        for (String parser : parsers()) {
            long parserTotal = outcomes.stream().filter(o -> o.evalCase().parserName().equals(parser)).count();
            long parserPassed = outcomes.stream()
                    .filter(o -> o.evalCase().parserName().equals(parser) && o.passed()).count();
            sb.append("  ").append(parser).append(": ").append(parserPassed).append('/')
                    .append(parserTotal).append('\n');
        }

        for (EvalOutcome failure : failures()) {
            sb.append("  FAILED [").append(failure.evalCase().parserName()).append('/')
                    .append(failure.evalCase().category()).append("] ")
                    .append(failure.evalCase().description())
                    .append(" -> ").append(failure.failureDetail()).append('\n');
        }

        return sb.toString();
    }
}
