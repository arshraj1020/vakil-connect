package com.arshraj.vakilconnect.ai.rag;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.embedding.Embedding;
import com.arshraj.vakilconnect.ai.embedding.EmbeddingClient;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vector search, against REAL PostgreSQL with pgvector and the REAL SQL.
 *
 * ================== WHY THE STUB EMBEDDER MAKES THIS TESTABLE ==============
 *
 * StubEmbeddingClient derives its vector from a SHA-256 of the text, so it is
 * deterministic but semantically meaningless. That sounds like it would make
 * retrieval untestable - and it would, if these tests tried to assert that the
 * semantically best chunk came back.
 *
 * They do not. They exploit the determinism instead: embedding a query whose
 * text EXACTLY matches a chunk produces the identical vector, so cosine
 * distance is 0. Two unrelated texts produce near-orthogonal 768-dimensional
 * vectors, so distance is around 1.0. That cleanly separates "relevant" from
 * "irrelevant" with no semantics at all, which is exactly what is needed to
 * test topK, thresholds, ordering and - above all - OWNERSHIP.
 *
 * Semantic quality is a question for a real model and an evaluation set, not
 * for a unit of SQL.
 *
 * Rows are inserted directly rather than through the upload/ingest pipeline:
 * that gives exact control over content, status and owner, and keeps these
 * tests about the RETRIEVAL query. The query itself is entirely real.
 */
@DisplayName("PgVectorDocumentRetriever")
class DocumentRetrievalIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentRetriever retriever;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------- fixtures

    private UUID insertDocument(UUID ownerId, String filename, AiDocumentStatus status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_documents (id, user_id, filename, content_type, size_bytes,
                                          sha256, content, status, created_at, updated_at)
                VALUES (?, ?, ?, 'text/plain', 10, ?, ?, ?, now(), now())
                """, id, ownerId, filename, sha256("seed-" + id),
                "seed".getBytes(StandardCharsets.UTF_8), status.name());
        return id;
    }

    /** Inserts a chunk whose embedding is the stub's vector for its own text. */
    private void insertChunk(UUID documentId, int index, String content) {
        Embedding embedding = embeddingClient.embed(content);
        jdbcTemplate.update("""
                INSERT INTO ai_document_chunks (id, document_id, chunk_index, content,
                                                content_hash, char_count, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::vector, now())
                """, UUID.randomUUID(), documentId, index, content,
                sha256(content), content.length(), embedding.toPgVectorLiteral());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID ownerOf(String email) {
        return userRepositoryForSupport.findByEmail(email).orElseThrow().getId();
    }

    private List<RetrievedChunk> search(UUID ownerId, String queryText) {
        return retriever.retrieve(ownerId, embeddingClient.embed(queryText));
    }

    // ============================== OWNERSHIP ==============================

    @Test
    @DisplayName("USER A CANNOT RETRIEVE USER B'S CHUNKS - enforced in the SQL")
    void ownershipIsEnforcedInTheQuery() throws Exception {
        /*
         * THE MOST IMPORTANT TEST IN AI-3.
         *
         * Both users hold a chunk with IDENTICAL text, so both chunks have an
         * identical embedding and both are at distance 0 from the query. There
         * is no similarity difference for the query to discriminate on - the
         * ONLY thing that can exclude B's row is the ownership predicate in the
         * WHERE clause.
         *
         * If ownership were applied in Java after the fact, this test would
         * still pass while the database had already handed the application
         * another user's document text. So it also asserts, separately, that
         * B's chunk id never appears in A's result at all.
         */
        String aliceEmail = distinctEmail("alice");
        String bobEmail = distinctEmail("bob");
        registerAndLoginClient(aliceEmail);
        registerAndLoginClient(bobEmail);

        UUID alice = ownerOf(aliceEmail);
        UUID bob = ownerOf(bobEmail);

        String shared = "The notice period for termination is thirty days.";

        UUID aliceDoc = insertDocument(alice, "alice.pdf", AiDocumentStatus.READY);
        UUID bobDoc = insertDocument(bob, "bob-confidential.pdf", AiDocumentStatus.READY);
        insertChunk(aliceDoc, 0, shared);
        insertChunk(bobDoc, 0, shared);

        List<RetrievedChunk> forAlice = search(alice, shared);

        assertEquals(1, forAlice.size(),
                "identical text in two users' documents; only the owner's may be returned");
        assertEquals(aliceDoc, forAlice.get(0).documentId());
        assertFalse(forAlice.stream().anyMatch(c -> c.documentId().equals(bobDoc)),
                "another user's chunk reached the application");
        assertFalse(forAlice.stream().anyMatch(c -> c.documentName().contains("bob")));

        // Symmetric: Bob sees his own and not Alice's.
        List<RetrievedChunk> forBob = search(bob, shared);
        assertEquals(1, forBob.size());
        assertEquals(bobDoc, forBob.get(0).documentId());
    }

    @Test
    @DisplayName("a user with no documents retrieves nothing, never a fallback to others'")
    void emptyCorpusReturnsNothing() throws Exception {
        String strangerEmail = distinctEmail("stranger");
        String ownerEmail = distinctEmail("owner");
        registerAndLoginClient(strangerEmail);
        registerAndLoginClient(ownerEmail);

        UUID doc = insertDocument(ownerOf(ownerEmail), "owned.pdf", AiDocumentStatus.READY);
        insertChunk(doc, 0, "Highly relevant text about deposits.");

        assertTrue(search(ownerOf(strangerEmail), "Highly relevant text about deposits.").isEmpty(),
                "an empty corpus must return nothing, not somebody else's best match");
    }

    // ============================== FILTERING ==============================

    @Test
    @DisplayName("an exactly-matching chunk is retrieved at distance ~0")
    void relevantChunkIsRetrieved() throws Exception {
        String email = uniqueEmail("relevant");
        registerAndLoginClient(email);
        UUID doc = insertDocument(ownerOf(email), "lease.pdf", AiDocumentStatus.READY);

        String text = "Clause 3. Either party may terminate on thirty days written notice.";
        insertChunk(doc, 3, text);

        List<RetrievedChunk> results = search(ownerOf(email), text);

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).chunkIndex());
        assertEquals("lease.pdf", results.get(0).documentName());
        assertTrue(results.get(0).distance() < 0.0001,
                "identical text must embed identically, got " + results.get(0).distance());
    }

    @Test
    @DisplayName("an unrelated chunk is EXCLUDED by the distance threshold")
    void irrelevantChunkIsFilteredOut() throws Exception {
        // Two unrelated hash-derived vectors in 768 dimensions are
        // near-orthogonal, so distance lands around 1.0 - well outside the 0.6
        // ceiling. This is the threshold doing its job.
        String email = uniqueEmail("irrelevant");
        registerAndLoginClient(email);
        UUID doc = insertDocument(ownerOf(email), "lease.pdf", AiDocumentStatus.READY);
        insertChunk(doc, 0, "Clause 3. Termination requires thirty days notice.");

        assertTrue(search(ownerOf(email), "completely unrelated question about tax rebates")
                        .isEmpty(),
                "an unrelated query must fall outside the distance threshold");
    }

    @Test
    @DisplayName("only READY documents contribute - a reprocessing document is skipped")
    void onlyReadyDocumentsAreRetrieved() throws Exception {
        /*
         * A document mid-reprocessing still holds its PREVIOUS chunks. Answering
         * from superseded text without saying so is worse than reporting
         * insufficient evidence. Not a security filter - those rows belong to
         * the same user - a freshness one.
         */
        String email = uniqueEmail("freshness");
        registerAndLoginClient(email);
        UUID owner = ownerOf(email);

        String text = "Clause 7. The deposit is refundable within thirty days.";

        UUID processing = insertDocument(owner, "stale.pdf", AiDocumentStatus.PROCESSING);
        insertChunk(processing, 0, text);
        assertTrue(search(owner, text).isEmpty(), "a PROCESSING document must not be retrieved");

        UUID pending = insertDocument(owner, "pending.pdf", AiDocumentStatus.PENDING);
        insertChunk(pending, 0, text);
        assertTrue(search(owner, text).isEmpty(), "a PENDING document must not be retrieved");

        UUID ready = insertDocument(owner, "current.pdf", AiDocumentStatus.READY);
        insertChunk(ready, 0, text);
        List<RetrievedChunk> results = search(owner, text);
        assertEquals(1, results.size(), "only the READY document may contribute");
        assertEquals("current.pdf", results.get(0).documentName());
    }

    // =============================== RANKING ===============================

    @Test
    @DisplayName("results are ordered by ASCENDING distance - most similar first")
    void resultsAreOrderedByDistance() throws Exception {
        String email = uniqueEmail("ranking");
        registerAndLoginClient(email);
        UUID owner = ownerOf(email);
        UUID doc = insertDocument(owner, "lease.pdf", AiDocumentStatus.READY);

        String exact = "The security deposit is two hundred and seventy thousand rupees.";
        insertChunk(doc, 0, "Some other clause about parking arrangements entirely.");
        insertChunk(doc, 1, exact);
        insertChunk(doc, 2, "Another unrelated clause concerning pets and visitors.");

        List<RetrievedChunk> results = search(owner, exact);

        assertFalse(results.isEmpty());
        assertEquals(1, results.get(0).chunkIndex(),
                "the exact match must rank first");
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).distance() <= results.get(i).distance(),
                    "distances must ascend: " + results.get(i - 1).distance()
                            + " then " + results.get(i).distance());
        }
    }

    @Test
    @DisplayName("chunk_index breaks distance ties, so ordering is DETERMINISTIC")
    void chunkIndexBreaksTies() throws Exception {
        /*
         * Identical text in three chunks means three identical vectors and three
         * identical distances. Without the chunk_index tiebreaker they come back
         * in whatever order the scan produced, so the same question could cite
         * different sources on consecutive calls - and a "deterministic
         * retrieval" claim would be false.
         */
        String email = uniqueEmail("tiebreak");
        registerAndLoginClient(email);
        UUID owner = ownerOf(email);
        UUID doc = insertDocument(owner, "lease.pdf", AiDocumentStatus.READY);

        String text = "Identical clause text repeated across sections.";
        insertChunk(doc, 5, text);
        insertChunk(doc, 2, text);
        insertChunk(doc, 9, text);

        List<Integer> first = search(owner, text).stream()
                .map(RetrievedChunk::chunkIndex).toList();
        List<Integer> second = search(owner, text).stream()
                .map(RetrievedChunk::chunkIndex).toList();

        assertEquals(List.of(2, 5, 9), first, "ties must resolve by ascending chunk_index");
        assertEquals(first, second, "repeated queries must return the same order");
    }

    @Test
    @DisplayName("topK caps the result count")
    void topKIsEnforced() throws Exception {
        String email = uniqueEmail("topk");
        registerAndLoginClient(email);
        UUID owner = ownerOf(email);
        UUID doc = insertDocument(owner, "lease.pdf", AiDocumentStatus.READY);

        String text = "Repeated relevant clause text.";
        for (int i = 0; i < 10; i++) {
            insertChunk(doc, i, text);
        }

        // Configured topK is 6 (application-test.yaml pins the production value).
        assertEquals(6, search(owner, text).size(), "topK must bound the result set");
    }

    @Test
    @DisplayName("several documents can contribute to one answer")
    void multipleDocumentsContribute() throws Exception {
        String email = uniqueEmail("multidoc");
        registerAndLoginClient(email);
        UUID owner = ownerOf(email);

        String text = "The governing law is the laws of India.";
        UUID lease = insertDocument(owner, "lease.pdf", AiDocumentStatus.READY);
        UUID employment = insertDocument(owner, "employment.docx", AiDocumentStatus.READY);
        insertChunk(lease, 0, text);
        insertChunk(employment, 0, text);

        List<RetrievedChunk> results = search(owner, text);

        assertEquals(2, results.size());
        assertEquals(2, results.stream().map(RetrievedChunk::documentId).distinct().count(),
                "a question may legitimately span the user's whole corpus");
    }

    @Test
    @DisplayName("a null owner is refused rather than producing an unscoped query")
    void nullOwnerIsRefused() {
        // A null owner would mean a WHERE clause with no ownership predicate.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> retriever.retrieve(null, embeddingClient.embed("anything")));
    }

    @Test
    @DisplayName("RetrievedChunk.toString never renders document text")
    void retrievedChunkToStringIsSafe() {
        RetrievedChunk chunk = new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(),
                "secret.pdf", 3, "CONFIDENTIAL SETTLEMENT TERMS", 0.12);

        String rendered = chunk.toString();

        assertFalse(rendered.contains("CONFIDENTIAL SETTLEMENT TERMS"));
        assertTrue(rendered.contains("not shown"));
    }
}
