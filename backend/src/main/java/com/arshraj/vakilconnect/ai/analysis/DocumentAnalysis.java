package com.arshraj.vakilconnect.ai.analysis;

import java.util.List;
import java.util.UUID;

/**
 * The body of {@code POST /api/ai/documents/{id}/analyze}.
 *
 * ================= WHERE EACH FIELD COMES FROM, EXACTLY ====================
 *
 * documentId  - THE DATABASE. The id the caller asked for, after an owner-scoped
 * documentName  read confirmed it exists and is theirs. Never the model's.
 * truncated   - THIS APPLICATION. Whether the context builder dropped chunks.
 *
 * everything else - the model, after strict parsing and bounding.
 *
 * The split is the whole security design of this DTO. A model asked to produce
 * JSON will happily produce a `documentId` field if it feels like one belongs
 * there, and if this record were deserialised straight from the model's reply,
 * that value would become the identity of the response. It is not deserialised
 * from the model's reply. AnalysisJsonParser reads six named fields and nothing
 * else; the identity is assembled here, in the service, from the row the
 * database returned.
 *
 * ================== WHY THE LISTS ARE STRINGS, NOT OBJECTS =================
 *
 * `parties` could plausibly be `[{"name": ..., "role": ...}]`, and richer
 * structure would be nicer to render. It is a list of strings because the
 * target is a 3B model running on a laptop: every additional required key is
 * another chance for the reply to be malformed, and a malformed reply fails the
 * WHOLE analysis rather than one field. A flat string per entry is the shape
 * small models get right most reliably, and "Landlord: Ramesh Kumar" carries
 * the same information a two-key object would.
 *
 * NO MODEL NAME AND NO PROVIDER FIELD. Which model answered is an internal
 * detail; publishing it here would put "llama3.2" in a response body and give a
 * client something to branch on that this project does not intend to keep
 * stable. AiMetrics already records it where operators can see it.
 *
 * EMPTY LIST MEANS "the document did not state any". It does NOT mean "the
 * model did not answer" - a reply that omits a category is rejected outright by
 * the parser, precisely so that the two cannot be confused here.
 */
public record DocumentAnalysis(

        /** From the database. Echoes the path variable, after ownership was proven. */
        UUID documentId,

        /** From the database. The sanitised filename stored at upload. */
        String documentName,

        /** Required from the model. Never blank - the parser refuses a blank one. */
        String summary,

        List<String> parties,

        List<String> importantDates,

        List<String> obligations,

        List<String> keyClauses,

        List<String> risks,

        /**
         * True when the document was larger than the configured context budget
         * and only its leading portion was analysed.
         *
         * Surfaced rather than hidden: a user reading a five-line summary of a
         * sixty-page agreement needs to know whether that is the document being
         * short or the analysis being partial.
         */
        boolean truncated) {

    /**
     * Assembles the response, taking identity from the CALLER and content from
     * the parsed model output.
     *
     * A single factory rather than a public constructor call at the use site,
     * so there is exactly one place where the two sources meet and it can be
     * read in one screen.
     */
    static DocumentAnalysis of(UUID documentId, String documentName,
                               AnalysisContent content, boolean truncated) {
        return new DocumentAnalysis(
                documentId,
                documentName,
                content.summary(),
                content.parties(),
                content.importantDates(),
                content.obligations(),
                content.keyClauses(),
                content.risks(),
                truncated);
    }

    /**
     * REDACTED. Every field except the two identifiers is derived from the
     * user's legal document - the summary especially, which is the densest
     * concentration of it anywhere in this feature. A record prints all its
     * components by default, so one {@code log.debug("{}", analysis)} would put
     * the substance of somebody's contract into the application log.
     */
    @Override
    public String toString() {
        return "DocumentAnalysis{documentId=" + documentId
                + ", parties=" + parties.size()
                + ", importantDates=" + importantDates.size()
                + ", obligations=" + obligations.size()
                + ", keyClauses=" + keyClauses.size()
                + ", risks=" + risks.size()
                + ", truncated=" + truncated
                + ", content=<not shown>}";
    }
}
