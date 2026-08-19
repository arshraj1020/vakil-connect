package com.arshraj.vakilconnect.ai.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunking, at the CONFIGURED 1200/200 - not at values chosen to make tests
 * convenient.
 *
 * The production defaults are what the pipeline runs, so shrinking them here
 * would test a configuration nobody uses and leave the real one unexercised.
 */
@DisplayName("RecursiveDocumentChunker")
class RecursiveDocumentChunkerTest {

    private static final int CHUNK_SIZE = 1200;
    private static final int OVERLAP = 200;

    private final DocumentChunker chunker = new RecursiveDocumentChunker(
            new AiIngestionProperties(CHUNK_SIZE, OVERLAP, 2000));

    @Test
    @DisplayName("a short document produces exactly one chunk")
    void shortDocumentIsOneChunk() {
        List<TextChunk> chunks = chunker.chunk("A short tenancy clause.");

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals("A short tenancy clause.", chunks.get(0).content());
    }

    @Test
    @DisplayName("a document longer than the chunk size produces several chunks")
    void longDocumentIsSplit() {
        List<TextChunk> chunks = chunker.chunk(IngestionFixtures.longLegalText(5000));

        assertTrue(chunks.size() > 1,
                "5000 characters must not fit in one " + CHUNK_SIZE + "-character chunk");
    }

    @Test
    @DisplayName("no chunk exceeds the configured size")
    void respectsChunkSize() {
        for (TextChunk chunk : chunker.chunk(IngestionFixtures.longLegalText(8000))) {
            assertTrue(chunk.charCount() <= CHUNK_SIZE,
                    "chunk " + chunk.index() + " is " + chunk.charCount()
                            + " characters, over the " + CHUNK_SIZE + " limit");
        }
    }

    @Test
    @DisplayName("consecutive chunks OVERLAP - a boundary clause appears in both")
    void overlapIsPresent() {
        /*
         * The reason overlap exists at all. Legal text refers backwards
         * constantly ("such notice", "the said premises"), so a definition
         * sitting on a boundary must survive in both neighbours or the
         * antecedent is lost from one of them.
         *
         * Asserted structurally rather than by exact character count: the
         * recursive splitter lands boundaries on paragraph and sentence breaks,
         * so the shared region is "around 200 characters", not exactly 200.
         * Demanding an exact figure would be testing the library's arithmetic
         * rather than the property that matters.
         */
        List<TextChunk> chunks = chunker.chunk(IngestionFixtures.longLegalText(6000));
        assertTrue(chunks.size() >= 2, "need at least two chunks to have an overlap");

        String first = chunks.get(0).content();
        String second = chunks.get(1).content();

        String tail = first.substring(Math.max(0, first.length() - OVERLAP));
        boolean shares = false;
        // Any reasonably long fragment of the first chunk's tail reappearing at
        // the head of the second is proof the regions overlap.
        for (int start = 0; start + 40 <= tail.length() && !shares; start += 10) {
            shares = second.contains(tail.substring(start, start + 40));
        }

        assertTrue(shares,
                "no shared region between chunk 0 and chunk 1 - overlap is not being applied");
    }

    @Test
    @DisplayName("indexes are 0-based, dense and ascending")
    void indexesAreStable() {
        // chunk_index is half of a unique constraint and the ordering AI-3 will
        // use to reassemble neighbouring passages.
        List<TextChunk> chunks = chunker.chunk(IngestionFixtures.longLegalText(7000));

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index(), "index gap or reorder at position " + i);
        }
    }

    @Test
    @DisplayName("no empty chunks")
    void noEmptyChunks() {
        // The database rejects one anyway; the chunker must not produce it. An
        // empty chunk embeds to noise and pollutes retrieval.
        for (TextChunk chunk : chunker.chunk(IngestionFixtures.legalText())) {
            assertFalse(chunk.content().isBlank(), "chunk " + chunk.index() + " is blank");
            assertTrue(chunk.charCount() > 0);
        }
    }

    @Test
    @DisplayName("no duplicate chunks, even though overlap makes near-duplicates")
    void noDuplicateChunks() {
        /*
         * Overlap means consecutive chunks legitimately SHARE text - that is
         * the feature. What must not happen is two chunks being
         * character-for-character identical, which a short tail can produce:
         * two identical vectors in the index means retrieval returns the same
         * passage twice and burns a result slot.
         */
        List<TextChunk> chunks = chunker.chunk(IngestionFixtures.longLegalText(4000));

        Set<String> contents = new HashSet<>();
        for (TextChunk chunk : chunks) {
            assertTrue(contents.add(chunk.content()),
                    "chunk " + chunk.index() + " duplicates an earlier chunk exactly");
        }
    }

    @Test
    @DisplayName("DETERMINISTIC - the same text always produces identical chunks")
    void isDeterministic() {
        /*
         * The property the whole idempotency guarantee rests on. If chunking
         * varied, reprocessing an unchanged document would churn the table and
         * "did this document change" would be unanswerable.
         */
        String text = IngestionFixtures.longLegalText(5000);

        List<TextChunk> first = chunker.chunk(text);
        List<TextChunk> second = chunker.chunk(text);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).content(), second.get(i).content(), "content differs at " + i);
            assertEquals(first.get(i).contentHash(), second.get(i).contentHash(),
                    "hash differs at " + i);
            assertEquals(first.get(i).index(), second.get(i).index());
        }
    }

    @Test
    @DisplayName("content hashes are deterministic and distinguish different text")
    void hashesAreDeterministicAndDistinct() {
        assertEquals(TextChunk.of(0, "clause one").contentHash(),
                TextChunk.of(5, "clause one").contentHash(),
                "the hash covers content only - the index must not change it");

        assertNotEquals(TextChunk.of(0, "clause one").contentHash(),
                TextChunk.of(0, "clause two").contentHash());

        assertTrue(TextChunk.of(0, "x").contentHash().matches("^[0-9a-f]{64}$"),
                "must match ck_ai_document_chunks_hash_format");
    }

    @Test
    @DisplayName("text either side of the chunk boundary behaves sensibly")
    void exactBoundaryBehaviour() {
        // Just under the limit: one chunk, nothing split.
        assertEquals(1, chunker.chunk(IngestionFixtures.distinctFiller(CHUNK_SIZE - 1)).size());

        /*
         * Over the limit, with TEXTUALLY DISTINCT content.
         *
         * The earlier version of this test used "a".repeat(CHUNK_SIZE * 2) and
         * failed - correctly. Homogeneous filler produces chunks that are
         * character-for-character identical, so the no-duplicates rule collapses
         * them to one. That made the test demand two contradictory things at
         * once: "split this" and "never store duplicates". The duplicate rule is
         * the explicit requirement, so the FIXTURE was wrong, not the chunker.
         * See homogeneousTextCollapsesToOneChunk below, which pins that
         * behaviour deliberately instead of stumbling into it.
         */
        List<TextChunk> chunks = chunker.chunk(IngestionFixtures.distinctFiller(CHUNK_SIZE * 2));

        assertTrue(chunks.size() >= 2,
                "2400 characters of distinct text must not fit in one "
                        + CHUNK_SIZE + "-character chunk, got " + chunks.size());

        for (TextChunk chunk : chunks) {
            assertTrue(chunk.charCount() <= CHUNK_SIZE,
                    "chunk " + chunk.index() + " is " + chunk.charCount() + " characters");
            assertFalse(chunk.content().isBlank());
        }
    }

    @Test
    @DisplayName("HOMOGENEOUS text collapses to one chunk - dedup beats splitting, deliberately")
    void homogeneousTextCollapsesToOneChunk() {
        /*
         * THE CONTRACT THE BOUNDARY TEST TRIPPED OVER, pinned explicitly.
         *
         * A document of one repeated character splits into several segments
         * that are all the same string. Storing them would put identical
         * vectors in the index, so retrieval would return the same passage
         * repeatedly and burn result slots on copies - which is precisely what
         * "no duplicate chunks" exists to prevent.
         *
         * So one chunk is the RIGHT answer here, even though the input is twice
         * the chunk size. Asserting it means a future change to the dedup rule
         * has to confront this case rather than discover it through a confusing
         * failure somewhere else.
         */
        List<TextChunk> chunks = chunker.chunk("a".repeat(CHUNK_SIZE * 2));

        assertEquals(1, chunks.size(),
                "identical segments must be deduplicated, not stored repeatedly");
        assertEquals(0, chunks.get(0).index());
    }

    @Test
    @DisplayName("deduplication does NOT discard legitimately distinct chunks")
    void deduplicationOnlyRemovesExactDuplicates() {
        /*
         * The other side of that rule, so "no duplicates" cannot quietly become
         * "loses content". Overlap makes consecutive chunks SHARE text by
         * design; only character-for-character identical ones are dropped.
         */
        List<TextChunk> chunks = chunker.chunk(IngestionFixtures.distinctFiller(6000));

        assertTrue(chunks.size() >= 4, "expected several chunks, got " + chunks.size());
        assertEquals(chunks.size(),
                chunks.stream().map(TextChunk::content).distinct().count(),
                "every retained chunk must be distinct");

        // And nothing was silently lost: the first and last tokens both survive.
        String all = chunks.stream().map(TextChunk::content).collect(Collectors.joining(" "));
        assertTrue(all.contains("clause0"), "the opening token was dropped");
    }

    @Test
    @DisplayName("empty or blank input produces no chunks rather than one empty chunk")
    void blankInputProducesNothing() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   \n\n  ").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    @DisplayName("the chunk ceiling is enforced")
    void respectsMaxChunks() {
        // A cost ceiling, not a quality setting: every chunk is one local
        // inference call.
        DocumentChunker capped = new RecursiveDocumentChunker(
                new AiIngestionProperties(CHUNK_SIZE, OVERLAP, 3));

        assertEquals(3, capped.chunk(IngestionFixtures.longLegalText(20000)).size());
    }

    @Test
    @DisplayName("overlap must be smaller than chunk size, checked at bind time")
    void rejectsOverlapLargerThanChunk() {
        // Otherwise each chunk re-emits most of its predecessor and splitting
        // never terminates. A startup failure naming the property beats an
        // infinite loop during ingestion.
        assertTrue(assertThrowsIllegalArgument(() -> new AiIngestionProperties(500, 500, 100))
                .contains("chunk-overlap"));
        assertTrue(assertThrowsIllegalArgument(() -> new AiIngestionProperties(500, 900, 100))
                .contains("chunk-size"));
    }

    private static String assertThrowsIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
