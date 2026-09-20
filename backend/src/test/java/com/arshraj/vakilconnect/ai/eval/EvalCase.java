package com.arshraj.vakilconnect.ai.eval;

import org.junit.jupiter.api.function.Executable;

/**
 * One quality check for one parser: a name, the {@link QualityCategory} it
 * proves, and an {@link Executable} that throws (any Throwable, typically
 * {@link AssertionError}) if the property does not hold.
 *
 * DELIBERATELY NOT THE SAME THING AS A JUnit {@code @Test} METHOD. A
 * per-parser test class already exists for each parser
 * (AnalysisJsonParserTest, ComparisonJsonParserTest) and stays exactly as
 * useful as before - those are the detailed, named tests a failure report
 * points a developer at. EvalCase exists one level up: it lets
 * QualityHarnessTest treat "parser X, category Y" as one data point it can
 * aggregate, tally, and check for completeness across every parser at once,
 * which a plain JUnit class cannot express without reflection.
 */
public record EvalCase(String parserName, QualityCategory category, String description,
                        Executable check) {
}
