package com.arshraj.vakilconnect.ai.eval;

/**
 * The result of running one {@link EvalCase}.
 *
 * {@code failureDetail} is populated ONLY from {@code Throwable.getMessage()}
 * of an exception thrown by the harness's OWN assertion code (JUnit's
 * {@code assertTrue}/{@code assertEquals} messages, or a fixed literal this
 * codebase wrote) - never from a parsed model reply. Every EvalCase is built
 * from fixtures the parser tests already establish do not leak model text
 * into exception messages (AnalysisJsonParser and ComparisonJsonParser both
 * throw only fixed-string {@code AiAnswerUnavailableException}s), so this
 * record inherits that guarantee rather than re-deriving it.
 */
public record EvalOutcome(EvalCase evalCase, boolean passed, String failureDetail) {

    static EvalOutcome pass(EvalCase evalCase) {
        return new EvalOutcome(evalCase, true, null);
    }

    static EvalOutcome fail(EvalCase evalCase, Throwable cause) {
        String detail = cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        return new EvalOutcome(evalCase, false, detail);
    }
}
