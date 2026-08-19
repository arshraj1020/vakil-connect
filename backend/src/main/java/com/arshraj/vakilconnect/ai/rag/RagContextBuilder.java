package com.arshraj.vakilconnect.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns retrieved chunks into a bounded, labelled context block.
 *
 * TWO JOBS, BOTH LOAD-BEARING.
 *
 * 1. BOUNDING. Never concatenate every chunk. topK x chunkSize is 7200
 *    characters at the current settings, and a larger topK later would silently
 *    push past a small local model's window - where the thing that falls out
 *    first is the SYSTEM PROMPT at the top. A context limit is therefore a
 *    prompt-injection control as much as a cost one.
 *
 * 2. LABELLING. Each chunk is prefixed [Source N] so the model can refer to
 *    evidence by number. Note what this does NOT do: the label is for the
 *    model's prose only. The structured citations returned to the client come
 *    from the retrieval list, never from parsing what the model wrote. See
 *    RagServiceImpl.
 *
 * TRUNCATION IS DETERMINISTIC: whole chunks, in retrieval order, until the next
 * one would not fit. Never a half chunk - a clause cut mid-sentence is worse
 * than an absent one, and a partial chunk still carrying its [Source N] label
 * would let the model cite evidence it only half saw.
 */
@Component
public class RagContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(RagContextBuilder.class);

    private final AiRetrievalProperties properties;

    public RagContextBuilder(AiRetrievalProperties properties) {
        this.properties = properties;
    }

    public RagContext build(List<RetrievedChunk> retrieved) {
        if (retrieved == null || retrieved.isEmpty()) {
            return new RagContext(List.of(), "", false);
        }

        List<RetrievedChunk> included = new ArrayList<>();
        StringBuilder rendered = new StringBuilder();
        boolean truncated = false;

        for (RetrievedChunk chunk : retrieved) {
            String block = render(included.size() + 1, chunk);

            if (rendered.length() + block.length() > properties.maxContextCharacters()) {
                /*
                 * STOP, DO NOT SKIP. Continuing to look for a smaller chunk that
                 * fits would reorder the evidence relative to relevance, so the
                 * model would see source 3 labelled as source 2 and the numbers
                 * in its prose would stop matching the ranking. Retrieval order
                 * is by descending relevance, so stopping keeps the best.
                 */
                truncated = true;
                break;
            }

            rendered.append(block);
            included.add(chunk);
        }

        if (truncated) {
            log.debug("Context truncated at {} of {} chunks ({} character budget)",
                    included.size(), retrieved.size(), properties.maxContextCharacters());
        }

        return new RagContext(List.copyOf(included), rendered.toString(), truncated);
    }

    /**
     * One labelled block.
     *
     * The document NAME is included because a citation the user can act on says
     * "rental-agreement.pdf, chunk 4", not a UUID. The document ID is
     * deliberately NOT put in the prompt: it is an internal identifier, the
     * model has no use for it, and printing it invites the model to echo
     * database keys into prose.
     */
    private String render(int sourceNumber, RetrievedChunk chunk) {
        return "[Source " + sourceNumber + "]\n"
                + "Document: " + chunk.documentName() + "\n"
                + "Chunk: " + chunk.chunkIndex() + "\n"
                + chunk.content() + "\n\n";
    }
}
