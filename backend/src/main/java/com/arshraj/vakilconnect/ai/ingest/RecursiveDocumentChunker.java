package com.arshraj.vakilconnect.ai.ingest;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits text with LangChain4j's recursive splitter.
 *
 * WHY A DEPENDENCY FOR THIS, WHEN AI-0 AND AI-1 ADDED NONE.
 *
 * The naive version - walk the string in fixed-size steps with an overlap - is
 * about ten lines, and it is the reason so many RAG demos retrieve badly. It
 * cuts mid-sentence and mid-word, so a chunk begins "...ereinafter referred to
 * as the Tenant) shall" and embeds to something meaningless. Recursive
 * splitting instead tries paragraph boundaries first, falls back to sentences,
 * then words, and only cuts mid-word when a single word exceeds the limit -
 * so boundaries land where the text already had them.
 *
 * That is genuinely fiddly to implement well, and the failure mode is silent:
 * everything works, retrieval is just quietly worse. It is the one thing in
 * AI-2 where a library earns its place, which is exactly why langchain4j is the
 * only new AI dependency and langchain4j-ollama and langchain4j-pgvector were
 * refused.
 *
 * MEASURED IN CHARACTERS, NOT TOKENS. DocumentSplitters.recursive() can take a
 * Tokenizer, and a token-based limit would fit the model's context window more
 * precisely - but the tokenizer must match the embedding model, and the
 * embedding model is configuration. A character limit is model-independent,
 * exactly reproducible, and comfortably inside nomic-embed-text's 8192-token
 * window at the configured sizes.
 */
@Component
public class RecursiveDocumentChunker implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(RecursiveDocumentChunker.class);

    private final AiIngestionProperties properties;

    public RecursiveDocumentChunker(AiIngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                properties.chunkSize(), properties.chunkOverlap());

        List<TextSegment> segments = splitter.split(Document.from(text));

        /*
         * DEDUPLICATED BY CONTENT, and this is not defensive padding.
         *
         * Overlap means consecutive chunks legitimately share text - that is
         * the feature. But a short document, or one whose tail is smaller than
         * the overlap, can make the splitter emit a final segment that is
         * character-for-character identical to its predecessor. Storing both
         * would put two identical vectors in the index, so retrieval would
         * return the same passage twice and burn one of the caller's result
         * slots on a duplicate.
         *
         * LinkedHashSet preserves first-seen order, so indexes stay ascending
         * and deterministic after the filter.
         */
        Set<String> seen = new LinkedHashSet<>();
        List<TextChunk> chunks = new ArrayList<>();

        for (TextSegment segment : segments) {
            String content = segment.text() == null ? "" : segment.text().strip();

            // The database rejects empty chunks; the chunker must not produce
            // them in the first place.
            if (content.isEmpty() || !seen.add(content)) {
                continue;
            }

            chunks.add(TextChunk.of(chunks.size(), content));

            /*
             * THE COST CEILING, enforced here rather than after embedding.
             *
             * Every chunk is one local inference call. Stopping at the limit
             * costs the tail of a pathological document; not stopping costs
             * minutes of CPU per upload. Truncation is logged so it is never
             * silent, and the document still becomes READY - a partial index
             * of an enormous file is more useful than a failure.
             */
            if (chunks.size() >= properties.maxChunksPerDocument()) {
                log.warn("Document reached the {}-chunk ceiling; the remainder was not indexed",
                        properties.maxChunksPerDocument());
                break;
            }
        }

        // Counts only. Never a fragment of the text.
        log.debug("Split {} characters into {} chunks (size={}, overlap={})",
                text.length(), chunks.size(), properties.chunkSize(), properties.chunkOverlap());

        return List.copyOf(chunks);
    }
}
