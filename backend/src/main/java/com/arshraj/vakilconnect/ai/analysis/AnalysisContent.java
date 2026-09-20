package com.arshraj.vakilconnect.ai.analysis;

import java.util.List;

/**
 * Everything the MODEL is allowed to contribute to an analysis.
 *
 * A SEPARATE TYPE FROM DocumentAnalysis, AND THAT IS THE POINT. The parser
 * returns this; the service turns it into a DocumentAnalysis by adding the
 * document's identity from the database. Because this record has no
 * documentId and no documentName, there is no field for a model-supplied
 * identifier to land in - the guarantee is enforced by the type system rather
 * than by a reviewer remembering to check.
 *
 * Every component is non-null by the time the parser returns one: the summary
 * is non-blank, and each list is present (possibly empty), bounded in length,
 * and contains only non-blank strings.
 */
record AnalysisContent(
        String summary,
        List<String> parties,
        List<String> importantDates,
        List<String> obligations,
        List<String> keyClauses,
        List<String> risks) {

    /** REDACTED - all six components are derived from the user's document. */
    @Override
    public String toString() {
        return "AnalysisContent{summaryChars=" + (summary == null ? 0 : summary.length())
                + ", parties=" + parties.size()
                + ", importantDates=" + importantDates.size()
                + ", obligations=" + obligations.size()
                + ", keyClauses=" + keyClauses.size()
                + ", risks=" + risks.size()
                + ", content=<not shown>}";
    }
}
