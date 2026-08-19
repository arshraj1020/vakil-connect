package com.arshraj.vakilconnect.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Context assembly: bounding, deterministic truncation, source alignment. */
@DisplayName("RagContextBuilder")
class RagContextBuilderTest {

    private static final int BUDGET = 8000;

    private final RagContextBuilder builder =
            new RagContextBuilder(new AiRetrievalProperties(6, 0.6, BUDGET, 4000));

    @Test
    @DisplayName("each chunk is labelled with its source number, document and index")
    void chunksAreLabelled() {
        RagContext context = builder.build(List.of(
                RagFixtures.chunk(RagFixtures.DOCUMENT_A, RagFixtures.NAME_A, 4, "first", 0.1),
                RagFixtures.chunk(RagFixtures.DOCUMENT_B, RagFixtures.NAME_B, 7, "second", 0.2)));

        assertTrue(context.rendered().contains("[Source 1]"));
        assertTrue(context.rendered().contains("[Source 2]"));
        assertTrue(context.rendered().contains("Document: " + RagFixtures.NAME_A));
        assertTrue(context.rendered().contains("Chunk: 4"));
        assertTrue(context.rendered().contains("Chunk: 7"));
    }

    @Test
    @DisplayName("the internal document UUID is NOT put in the prompt")
    void documentIdIsNotExposedToTheModel() {
        // An internal key the model has no use for; printing it invites the
        // model to echo database identifiers into prose.
        RagContext context = builder.build(List.of(RagFixtures.chunk(0, "evidence", 0.1)));

        assertFalse(context.rendered().contains(RagFixtures.DOCUMENT_A.toString()));
    }

    @Test
    @DisplayName("source numbering follows retrieval order, which is relevance order")
    void numberingFollowsRelevance() {
        RagContext context = builder.build(List.of(
                RagFixtures.chunk(9, "most relevant", 0.05),
                RagFixtures.chunk(1, "less relevant", 0.40)));

        assertTrue(context.rendered().indexOf("[Source 1]")
                < context.rendered().indexOf("[Source 2]"));
        assertTrue(context.rendered().indexOf("most relevant")
                < context.rendered().indexOf("less relevant"));
    }

    @Test
    @DisplayName("context is BOUNDED and truncation is reported")
    void contextIsBounded() {
        RagContext context = builder.build(RagFixtures.chunks(12, 1200));

        assertTrue(context.rendered().length() <= BUDGET,
                "rendered " + context.rendered().length() + " characters over a "
                        + BUDGET + " budget");
        assertTrue(context.truncated(), "dropping evidence must never be silent");
        assertTrue(context.chunks().size() < 12);
    }

    @Test
    @DisplayName("truncation keeps the MOST RELEVANT chunks and stops - it never skips ahead")
    void truncationStopsRatherThanSkipping() {
        /*
         * Continuing past an oversized chunk to find a smaller one that fits
         * would reorder evidence relative to relevance, so [Source 2] in the
         * prompt would no longer be the second-best match - and the numbers in
         * the model's prose would stop matching the ranking.
         */
        RagContext context = builder.build(RagFixtures.chunks(12, 1200));

        for (int i = 0; i < context.chunks().size(); i++) {
            assertEquals(i, context.chunks().get(i).chunkIndex(),
                    "included chunks must be a contiguous prefix of retrieval order");
        }
    }

    @Test
    @DisplayName("truncation is DETERMINISTIC - the same input gives the same context")
    void truncationIsDeterministic() {
        var input = RagFixtures.chunks(12, 1200);

        RagContext first = builder.build(input);
        RagContext second = builder.build(input);

        assertEquals(first.rendered(), second.rendered());
        assertEquals(first.chunks().size(), second.chunks().size());
    }

    @Test
    @DisplayName("only WHOLE chunks are included - never half a clause")
    void neverIncludesAPartialChunk() {
        // A clause cut mid-sentence is worse than an absent one, and a partial
        // chunk still carrying its [Source N] label would let the model cite
        // evidence it only half saw.
        RagContext context = builder.build(RagFixtures.chunks(12, 1200));

        for (var chunk : context.chunks()) {
            assertTrue(context.rendered().contains(chunk.content()),
                    "chunk " + chunk.chunkIndex() + " was included only partially");
        }
    }

    @Test
    @DisplayName("empty retrieval yields an empty context, not a blank fence")
    void emptyRetrievalYieldsEmptyContext() {
        RagContext context = builder.build(List.of());

        assertTrue(context.isEmpty());
        assertEquals("", context.rendered());
        assertFalse(context.truncated());

        assertTrue(builder.build(null).isEmpty());
    }

    @Test
    @DisplayName("RagContext.toString never renders the assembled document text")
    void toStringIsSafe() {
        RagContext context = builder.build(
                List.of(RagFixtures.chunk(0, "CONFIDENTIAL SETTLEMENT TERMS", 0.1)));

        assertFalse(context.toString().contains("CONFIDENTIAL SETTLEMENT TERMS"));
        assertTrue(context.toString().contains("not shown"));
    }
}
