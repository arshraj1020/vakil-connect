package com.arshraj.vakilconnect.ai.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The context bound, and the truncation contract that goes with it.
 *
 * The interesting property is not "it stops" - it is WHERE it stops and WHAT IT
 * TELLS THE CALLER. A builder that quietly dropped the second half of a
 * contract would still pass a naive size assertion while producing an analysis
 * that reads like a complete one and is not.
 */
@DisplayName("AnalysisContextBuilder")
class AnalysisContextBuilderTest {

    private static final int BUDGET = 3000;

    private final AnalysisContextBuilder builder = new AnalysisContextBuilder(
            new AiAnalysisProperties(BUDGET, 2000, 20, 400));

    @Test
    @DisplayName("a short document fits whole and is not marked truncated")
    void shortDocumentIsNotTruncated() {
        AnalysisContext context = builder.build(List.of(AnalysisFixtures.CLAUSE));

        assertEquals(1, context.chunkCount());
        assertFalse(context.truncated());
        assertTrue(context.rendered().contains(AnalysisFixtures.CLAUSE));
    }

    @Test
    @DisplayName("no context ever exceeds the configured budget")
    void neverExceedsTheBudget() {
        AnalysisContext context = builder.build(AnalysisFixtures.distinctChunks(40, 1200));

        assertTrue(context.rendered().length() <= BUDGET,
                "context is " + context.rendered().length()
                        + " characters, over the " + BUDGET + " budget");
    }

    @Test
    @DisplayName("a document over the budget is truncated AND SAYS SO")
    void oversizedDocumentSetsTheFlag() {
        List<String> chunks = AnalysisFixtures.distinctChunks(10, 1200);

        AnalysisContext context = builder.build(chunks);

        assertTrue(context.truncated(), "dropping chunks must be reported");
        assertTrue(context.chunkCount() < chunks.size());
        assertTrue(context.chunkCount() > 0, "at least the leading chunks must survive");
    }

    @Test
    @DisplayName("LEADING chunks are kept, in document order")
    void keepsTheLeadingChunksInOrder() {
        /*
         * The documented strategy. Document order is the order that helps here:
         * the parties, the date and the recitals of a legal instrument are
         * almost always at the front, which is exactly what the analysis is
         * asked to extract.
         */
        List<String> chunks = AnalysisFixtures.distinctChunks(10, 1200);

        AnalysisContext context = builder.build(chunks);
        String rendered = context.rendered();

        assertTrue(rendered.contains("CHUNK-0-MARKER"), "the first chunk must be present");

        int previous = -1;
        for (int i = 0; i < context.chunkCount(); i++) {
            int at = rendered.indexOf("CHUNK-" + i + "-MARKER");
            assertTrue(at > previous, "chunk " + i + " is missing or out of order");
            previous = at;
        }

        assertFalse(rendered.contains("CHUNK-9-MARKER"),
                "a chunk past the budget must not appear");
    }

    @Test
    @DisplayName("STOPS at the budget - it does not skip ahead to a chunk that fits")
    void stopsRatherThanSkipping() {
        /*
         * The rule that keeps a truncated analysis honest. If an oversized chunk
         * were skipped and a later small one taken instead, the model would
         * receive a document with a hole in it and no indication there was one -
         * and it would summarise across the gap as though the text ran
         * continuously.
         *
         * Fixture: a small chunk, then one that alone exceeds the budget, then a
         * small one that WOULD fit. The third must be absent.
         */
        List<String> chunks = new ArrayList<>();
        chunks.add("FIRST-MARKER a short opening clause.");
        chunks.add("HUGE-MARKER " + "x".repeat(BUDGET + 500));
        chunks.add("THIRD-MARKER a short closing clause.");

        AnalysisContext context = builder.build(chunks);

        assertEquals(1, context.chunkCount());
        assertTrue(context.truncated());
        assertTrue(context.rendered().contains("FIRST-MARKER"));
        assertFalse(context.rendered().contains("HUGE-MARKER"),
                "a chunk over the budget must not be included");
        assertFalse(context.rendered().contains("THIRD-MARKER"),
                "the walk must STOP at the budget, not skip ahead for a chunk that fits");
    }

    @Test
    @DisplayName("chunks are whole - never cut mid-sentence")
    void includesWholeChunksOnly() {
        // A clause cut mid-sentence is worse than an absent clause: "the tenant
        // shall not" reads as an obligation the document does not contain.
        List<String> chunks = AnalysisFixtures.distinctChunks(10, 1200);

        AnalysisContext context = builder.build(chunks);

        for (int i = 0; i < context.chunkCount(); i++) {
            assertTrue(context.rendered().contains(chunks.get(i)),
                    "chunk " + i + " appears only partially");
        }
    }

    @Test
    @DisplayName("DETERMINISTIC - the same document always yields the same context")
    void isDeterministic() {
        /*
         * What makes two analyses of an unchanged document differ only by what
         * the model did, never by what it was shown. Chunking is already
         * deterministic (AI-2); this walk adds no tie-breaks and no randomness.
         */
        List<String> chunks = AnalysisFixtures.distinctChunks(10, 1200);

        AnalysisContext first = builder.build(chunks);
        AnalysisContext second = builder.build(chunks);

        assertEquals(first.rendered(), second.rendered());
        assertEquals(first.chunkCount(), second.chunkCount());
        assertEquals(first.truncated(), second.truncated());
    }

    @Test
    @DisplayName("an empty document produces an empty context, not a broken one")
    void emptyInputIsEmptyContext() {
        AnalysisContext empty = builder.build(List.of());
        assertTrue(empty.isEmpty());
        assertFalse(empty.truncated(), "nothing was dropped, so nothing was truncated");
        assertEquals("", empty.rendered());

        // null is defended against too: the service refuses an empty context
        // rather than letting one reach the model, and that guard must not
        // itself be reachable only through a NullPointerException.
        assertTrue(builder.build(null).isEmpty());
    }

    @Test
    @DisplayName("chunk boundaries are visible, so overlap does not read as emphasis")
    void chunksAreLabelled() {
        /*
         * AI-2 overlaps consecutive chunks by 200 characters, so a naive
         * concatenation repeats a sentence at every seam - and a model reading a
         * duplicated sentence will sometimes report the obligation in it twice.
         */
        AnalysisContext context = builder.build(List.of("first clause", "second clause"));

        assertTrue(context.rendered().contains("[Excerpt 1]"));
        assertTrue(context.rendered().contains("[Excerpt 2]"));
    }

    @Test
    @DisplayName("the filename is NOT in the prompt text")
    void filenameIsNotRendered() {
        // It would be user-supplied text inside the fence for no structural
        // gain: the response takes the name from the database, so the model has
        // no need to know it.
        AnalysisContext context = builder.build(List.of(AnalysisFixtures.CLAUSE));

        assertFalse(context.rendered().contains(AnalysisFixtures.DOCUMENT_NAME));
    }

    @Test
    @DisplayName("AnalysisContext.toString() does not print the document")
    void contextToStringIsSafe() {
        String rendered = builder.build(List.of(AnalysisFixtures.CLAUSE)).toString();

        assertFalse(rendered.contains("Rs 25,000"), "document text leaked through toString()");
        assertTrue(rendered.contains("<not shown>"));
    }
}
