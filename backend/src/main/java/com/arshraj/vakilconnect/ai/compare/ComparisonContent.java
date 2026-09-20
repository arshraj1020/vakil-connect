package com.arshraj.vakilconnect.ai.compare;

import java.util.List;

/**
 * Everything the MODEL is allowed to contribute to a comparison.
 *
 * Mirrors AI-4's {@code AnalysisContent} exactly and for the same reason: this
 * type has no identity components for either document, so a model told by an
 * injected document to relabel which side is which has nowhere to put that
 * claim. {@link DocumentComparisonServiceImpl} assembles the response by
 * pairing this with the two database rows the loader returned.
 */
record ComparisonContent(
        String summary,
        List<String> keyDifferences,
        List<String> onlyInFirst,
        List<String> onlyInSecond) {

    /** REDACTED - every component is derived from the two compared documents. */
    @Override
    public String toString() {
        return "ComparisonContent{summaryChars=" + (summary == null ? 0 : summary.length())
                + ", keyDifferences=" + keyDifferences.size()
                + ", onlyInFirst=" + onlyInFirst.size()
                + ", onlyInSecond=" + onlyInSecond.size()
                + ", content=<not shown>}";
    }
}
