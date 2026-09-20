package com.arshraj.vakilconnect.ai.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns one document's chunks into a bounded block of prompt text.
 *
 * ==================== THE SELECTION STRATEGY, DOCUMENTED ====================
 *
 * LEADING CHUNKS IN DOCUMENT ORDER, WHOLE CHUNKS ONLY, STOP AT THE BUDGET.
 *
 * The chunks arrive in ascending chunk_index - the document's own reading order,
 * because AiDocumentChunkRepository.findByDocumentAndOwner sorts by it. This
 * walks that order from the start and appends whole chunks until the next one
 * would exceed `max-context-characters`, then STOPS.
 *
 * Three decisions, each with an alternative that was rejected:
 *
 * 1. DOCUMENT ORDER, NOT RELEVANCE ORDER. AI-3 ranks by vector distance because
 *    it is answering a question. There is no question here, so there is nothing
 *    to rank against - and running a similarity search against a synthetic query
 *    like "parties dates obligations" would be a made-up ranking dressed as a
 *    principled one. Document order is also the order that helps: the parties,
 *    the date and the recitals of a legal instrument are almost always at the
 *    front, which is exactly what the analysis is asked to extract.
 *
 * 2. STOP, DO NOT SKIP AHEAD. Continuing past an oversized chunk to find a
 *    smaller one that fits would hand the model a document with a hole in it and
 *    no indication there was one - and the model would summarise across the gap
 *    as though the text were continuous. Stopping produces a prefix, which is
 *    honest and is reported as `truncated`.
 *
 * 3. WHOLE CHUNKS, NEVER A PARTIAL ONE. A clause cut mid-sentence is worse than
 *    an absent clause: "the tenant shall not" reads as an obligation the
 *    document does not contain. Same rule as RagContextBuilder.
 *
 * DETERMINISTIC. The same document always yields the same context, because
 * chunking is deterministic (AI-2) and this walk has no tie-breaks and no
 * randomness. Two analyses of an unchanged document therefore differ only by
 * whatever the model does, never by what it was shown.
 */
@Component
public class AnalysisContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(AnalysisContextBuilder.class);

    private final AiAnalysisProperties properties;

    public AnalysisContextBuilder(AiAnalysisProperties properties) {
        this.properties = properties;
    }

    public AnalysisContext build(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new AnalysisContext("", 0, false);
        }

        StringBuilder rendered = new StringBuilder();
        int included = 0;
        boolean truncated = false;

        for (String chunk : chunks) {
            String block = render(included + 1, chunk);

            if (rendered.length() + block.length() > properties.maxContextCharacters()) {
                truncated = true;
                break;
            }

            rendered.append(block);
            included++;
        }

        if (truncated) {
            // Counts only. Never a fragment of the text.
            log.debug("Analysis context bounded to {} of {} chunks ({} character budget)",
                    included, chunks.size(), properties.maxContextCharacters());
        }

        return new AnalysisContext(rendered.toString(), included, truncated);
    }

    /**
     * One labelled block.
     *
     * The label exists so the model can see chunk boundaries. AI-2's chunker
     * overlaps consecutive chunks by 200 characters, so a naive concatenation
     * repeats a sentence at every seam - and a model reading a duplicated
     * sentence will sometimes report the obligation in it twice. A visible
     * boundary makes the repetition legible as a boundary rather than as
     * emphasis.
     *
     * THE FILENAME IS DELIBERATELY NOT IN THE PROMPT. It would give the model
     * a little context ("this is a rental agreement"), and it is also
     * user-supplied text that would have to be trusted inside the fence for no
     * structural gain. The response takes the name from the database, so the
     * model has no need to know it.
     */
    private String render(int part, String chunk) {
        return "[Excerpt " + part + "]\n" + chunk + "\n\n";
    }
}
