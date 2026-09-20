package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.ai.analysis.AnalysisDocument;

import java.util.List;
import java.util.UUID;

/**
 * The body of {@code POST /api/ai/documents/compare}.
 *
 * IDENTITY OF BOTH DOCUMENTS COMES FROM THE DATABASE, EXACTLY AS IN AI-4.
 * {@code firstDocumentId}/{@code firstDocumentName} and their "second"
 * counterparts are read from the two rows {@link DocumentComparisonServiceImpl}
 * loaded before the model was ever called; the model contributes only
 * {@code summary}, {@code keyDifferences}, {@code onlyInFirst} and
 * {@code onlyInSecond} - see {@link ComparisonContent}, which has no field for
 * either document's identity to occupy.
 *
 * "FIRST" AND "SECOND" MATCH THE REQUEST FIELD ORDER
 * ({@code documentId}, {@code compareToDocumentId}), so a caller can always
 * tell which finding belongs to which upload without a second lookup.
 *
 * @param onlyInFirst  clauses or terms the model found in the first document
 *                     with no counterpart in the second
 * @param onlyInSecond the reverse
 * @param truncated    true when EITHER document was larger than its context
 *                     budget, so a thin comparison is explained rather than
 *                     presented as complete
 */
public record DocumentComparison(
        UUID firstDocumentId,
        String firstDocumentName,
        UUID secondDocumentId,
        String secondDocumentName,
        String summary,
        List<String> keyDifferences,
        List<String> onlyInFirst,
        List<String> onlyInSecond,
        boolean truncated) {

    static DocumentComparison of(AnalysisDocument first, AnalysisDocument second,
                                 ComparisonContent content, boolean truncated) {
        return new DocumentComparison(
                first.documentId(), first.documentName(),
                second.documentId(), second.documentName(),
                content.summary(), content.keyDifferences(),
                content.onlyInFirst(), content.onlyInSecond(),
                truncated);
    }

    /**
     * REDACTED. Every field but the two identifiers is derived from the
     * user's own documents - the summary and both difference lists especially.
     */
    @Override
    public String toString() {
        return "DocumentComparison{firstDocumentId=" + firstDocumentId
                + ", secondDocumentId=" + secondDocumentId
                + ", keyDifferences=" + keyDifferences.size()
                + ", onlyInFirst=" + onlyInFirst.size()
                + ", onlyInSecond=" + onlyInSecond.size()
                + ", truncated=" + truncated
                + ", content=<not shown>}";
    }
}
