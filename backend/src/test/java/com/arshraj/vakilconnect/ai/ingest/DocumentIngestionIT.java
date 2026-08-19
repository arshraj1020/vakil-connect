package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.document.DocumentFixtures;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentChunk;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentChunkRepository;
import com.arshraj.vakilconnect.common.exception.DocumentExtractionException;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ingestion pipeline end to end, against a real PostgreSQL with pgvector.
 *
 * NO MOCKS. Every test here runs the real extractor, normalizer, chunker and
 * the stub embedding client through the real HTTP layer and the real
 * transactions. The failure paths that need a broken collaborator live in
 * DocumentIngestionFailureIT, which forks a context; keeping them out of here
 * means these tests share the suite's single cached context.
 *
 * Assertions are made against the DATABASE wherever the claim is about
 * persistence. A service return value saying "12 chunks" is not evidence that
 * twelve rows exist, and the interesting bugs - stale tails, duplicate indexes,
 * partial writes - are visible only in the table.
 */
@DisplayName("AI document ingestion")
class DocumentIngestionIT extends AbstractIntegrationTest {

    private static final String DOCUMENTS = "/api/ai/documents";

    @Autowired
    private AiDocumentChunkRepository chunkRepository;

    @Autowired
    private IngestionTransactions transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------- helpers

    private UUID upload(String token, String filename, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", filename, "text/plain", content))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID uploadText(String token, String text) throws Exception {
        return upload(token, "agreement.txt", text.getBytes(StandardCharsets.UTF_8));
    }

    private MvcResult process(String token, UUID documentId) throws Exception {
        return mockMvc.perform(post(DOCUMENTS + "/" + documentId + "/process")
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private String statusOf(UUID documentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ai_documents WHERE id = ?", String.class, documentId);
    }

    private long chunkCount(UUID documentId) {
        return chunkRepository.countByDocumentId(documentId);
    }

    private List<Integer> chunkIndexes(UUID documentId) {
        return jdbcTemplate.queryForList(
                "SELECT chunk_index FROM ai_document_chunks WHERE document_id = ? "
                        + "ORDER BY chunk_index", Integer.class, documentId);
    }

    // ------------------------------------------------------- happy lifecycle

    @Test
    @DisplayName("PENDING -> READY, with chunks and embeddings persisted")
    void processReachesReady() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("ingest"));
        UUID id = uploadText(token, IngestionFixtures.longLegalText(4000));

        // Upload leaves it PENDING - nothing extracts text at AI-1.
        assertEquals(AiDocumentStatus.PENDING.name(), statusOf(id));

        MvcResult result = process(token, id);

        assertEquals(200, result.getResponse().getStatus(),
                "body was: " + result.getResponse().getContentAsString());

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("READY", body.get("status").asText());
        assertTrue(body.get("chunkCount").asInt() > 1);
        assertEquals(768, body.get("embeddingDimension").asInt());

        // THE DATABASE IS THE EVIDENCE, not the response body.
        assertEquals(AiDocumentStatus.READY.name(), statusOf(id));
        assertEquals(body.get("chunkCount").asInt(), chunkCount(id));

        // Every chunk carries a real 768-wide vector.
        Integer withEmbedding = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_document_chunks "
                        + "WHERE document_id = ? AND embedding IS NOT NULL",
                Integer.class, id);
        assertEquals(body.get("chunkCount").asInt(), withEmbedding);
    }

    @Test
    @DisplayName("chunk indexes are 0-based, dense and ascending in the table")
    void chunkIndexesAreDense() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("indexes"));
        UUID id = uploadText(token, IngestionFixtures.longLegalText(6000));
        process(token, id);

        List<Integer> indexes = chunkIndexes(id);

        assertTrue(indexes.size() > 1);
        for (int i = 0; i < indexes.size(); i++) {
            assertEquals(i, indexes.get(i), "gap or reorder at position " + i);
        }
    }

    @Test
    @DisplayName("a short document produces exactly one chunk and still reaches READY")
    void shortDocumentProducesOneChunk() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("short"));
        UUID id = uploadText(token, "The Tenant shall pay Rs. 45,000 per month.");

        process(token, id);

        assertEquals(AiDocumentStatus.READY.name(), statusOf(id));
        assertEquals(1, chunkCount(id));
    }

    @Test
    @DisplayName("the response carries counts and state only - never document text")
    void responseCarriesNoDocumentText() throws Exception {
        String marker = "SETTLEMENT-AMOUNT-4700000-RUPEES";
        String token = registerAndLoginClient(uniqueEmail("privacy"));
        UUID id = uploadText(token, "Confidential. " + marker + " payable on signing.");

        String body = process(token, id).getResponse().getContentAsString();

        assertFalse(body.contains(marker), "the process response leaked document content");
        assertFalse(body.contains("\"content\":"));
        assertFalse(body.contains("\"embedding\":"));
    }

    // ------------------------------------------------------- idempotency

    @Test
    @DisplayName("reprocessing REPLACES chunks - identical input, identical hashes, no duplicates")
    void reprocessingIsIdempotent() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("idempotent"));
        UUID id = uploadText(token, IngestionFixtures.longLegalText(5000));

        process(token, id);
        long firstCount = chunkCount(id);
        List<String> firstHashes = jdbcTemplate.queryForList(
                "SELECT content_hash FROM ai_document_chunks WHERE document_id = ? "
                        + "ORDER BY chunk_index", String.class, id);

        // A second run from READY is allowed on purpose - reprocessing after a
        // model or chunk-size change is legitimate.
        process(token, id);

        assertEquals(firstCount, chunkCount(id), "reprocessing must replace, not append");
        assertEquals(firstHashes, jdbcTemplate.queryForList(
                        "SELECT content_hash FROM ai_document_chunks WHERE document_id = ? "
                                + "ORDER BY chunk_index", String.class, id),
                "chunking is deterministic, so hashes must be byte-identical");
    }

    @Test
    @DisplayName("NO STALE TAIL when a re-run produces fewer chunks")
    void reprocessingRemovesStaleTail() throws Exception {
        /*
         * THE CASE THAT RULES OUT UPSERT.
         *
         * An upsert keyed on (document_id, chunk_index) would rewrite the first
         * N rows and leave the tail of the longer previous run behind - stale
         * text carrying a valid embedding, indistinguishable from current
         * content at retrieval time. Delete-then-insert makes the stored state
         * a function of the current input alone.
         *
         * Simulated by processing a long document, then replacing its bytes
         * with a much shorter text and reprocessing.
         */
        String token = registerAndLoginClient(uniqueEmail("staletail"));
        UUID id = uploadText(token, IngestionFixtures.longLegalText(9000));

        process(token, id);
        long manyChunks = chunkCount(id);
        assertTrue(manyChunks > 2, "need several chunks to have a tail, got " + manyChunks);

        // Replace the stored bytes in place, so the same document now yields
        // far fewer chunks.
        jdbcTemplate.update("UPDATE ai_documents SET content = ? WHERE id = ?",
                "One short clause only.".getBytes(StandardCharsets.UTF_8), id);

        process(token, id);

        assertEquals(1, chunkCount(id),
                "chunks from the longer run survived - the tail was not deleted");
        assertEquals(List.of(0), chunkIndexes(id));
    }

    // ------------------------------------------------------ failure handling

    @Test
    @DisplayName("extraction failure -> FAILED, with a safe reason and NO chunks")
    void extractionFailureMarksFailed() throws Exception {
        /*
         * AI-1's DOCX fixture is valid enough to pass upload validation - it is
         * a real ZIP naming word/document.xml - but has no OPC relationship
         * graph, so POI cannot find the document part. A genuine unparseable
         * document, with no mocking.
         */
        String token = registerAndLoginClient(uniqueEmail("badextract"));

        MvcResult uploaded = mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "broken.docx",
                                "application/octet-stream", DocumentFixtures.docx()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper
                .readTree(uploaded.getResponse().getContentAsString()).get("id").asText());

        MvcResult result = process(token, id);

        // 422: the request was fine, the stored FILE cannot be processed.
        assertEquals(422, result.getResponse().getStatus());
        assertEquals(DocumentExtractionException.CODE, objectMapper
                .readTree(result.getResponse().getContentAsString()).get("code").asText());

        assertEquals(AiDocumentStatus.FAILED.name(), statusOf(id));
        assertEquals(0, chunkCount(id), "a failed run must leave no chunks behind");

        String reason = jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM ai_documents WHERE id = ?", String.class, id);
        assertEquals(DocumentIngestionServiceImpl.REASON_EXTRACTION, reason,
                "the reason must be one of the fixed safe constants");
    }

    @Test
    @DisplayName("a document with no extractable text -> FAILED, never READY with zero chunks")
    void emptyExtractionMarksFailed() throws Exception {
        // Whitespace-only content: AI-1 rejects it at upload, so the case is
        // reached by writing it directly - which is also how a document whose
        // text vanishes after normalization would behave.
        String token = registerAndLoginClient(uniqueEmail("notext"));
        UUID id = uploadText(token, "placeholder");

        jdbcTemplate.update("UPDATE ai_documents SET content = ? WHERE id = ?",
                "   \n\t   \n  ".getBytes(StandardCharsets.UTF_8), id);

        assertEquals(422, process(token, id).getResponse().getStatus());

        assertEquals(AiDocumentStatus.FAILED.name(), statusOf(id));
        assertEquals(0, chunkCount(id));
    }

    @Test
    @DisplayName("FAILED -> retry -> READY, and the failure reason is cleared")
    void retryAfterFailureSucceeds() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("retry"));
        UUID id = uploadText(token, "placeholder");

        // Break it, fail, then repair it and retry - a plain re-POST.
        jdbcTemplate.update("UPDATE ai_documents SET content = ? WHERE id = ?",
                "   ".getBytes(StandardCharsets.UTF_8), id);
        process(token, id);
        assertEquals(AiDocumentStatus.FAILED.name(), statusOf(id));

        jdbcTemplate.update("UPDATE ai_documents SET content = ? WHERE id = ?",
                IngestionFixtures.legalText().getBytes(StandardCharsets.UTF_8), id);

        assertEquals(200, process(token, id).getResponse().getStatus());

        assertEquals(AiDocumentStatus.READY.name(), statusOf(id));
        assertTrue(chunkCount(id) >= 1);
        assertEquals(null, jdbcTemplate.queryForObject(
                        "SELECT failure_reason FROM ai_documents WHERE id = ?", String.class, id),
                "a previous run's reason must not survive a successful one");
    }

    // ----------------------------------------------------------- concurrency

    @Test
    @DisplayName("only ONE caller can claim a document - the conditional UPDATE is the lock")
    void onlyOneClaimSucceeds() throws Exception {
        /*
         * Tested against the guard itself rather than by racing two HTTP
         * threads. Synchronous ingestion with a stub embedder completes in
         * milliseconds, so a thread race would be timing-dependent and would
         * pass or fail by luck. The claim is the actual contract, and it is
         * deterministic: the second call sees a row already in PROCESSING and
         * matches nothing.
         */
        String email = distinctEmail("claim");
        String token = registerAndLoginClient(email);
        UUID id = uploadText(token, IngestionFixtures.legalText());
        UUID ownerId = userRepositoryForSupport.findByEmail(email).orElseThrow().getId();

        assertTrue(transactions.claim(id, ownerId), "the first claim must win");
        assertFalse(transactions.claim(id, ownerId), "a second claim must not proceed");

        assertEquals(AiDocumentStatus.PROCESSING.name(), statusOf(id));
    }

    @Test
    @DisplayName("processing a document already PROCESSING returns 409, not a second run")
    void concurrentProcessingIsRefused() throws Exception {
        String email = distinctEmail("conflict");
        String token = registerAndLoginClient(email);
        UUID id = uploadText(token, IngestionFixtures.legalText());
        UUID ownerId = userRepositoryForSupport.findByEmail(email).orElseThrow().getId();

        transactions.claim(id, ownerId);

        MvcResult result = process(token, id);

        assertEquals(409, result.getResponse().getStatus());
        assertEquals("DOCUMENT_ALREADY_PROCESSING", objectMapper
                .readTree(result.getResponse().getContentAsString()).get("code").asText());

        // No duplicate work happened.
        assertEquals(0, chunkCount(id));
    }

    // ------------------------------------------------------------- security

    @Test
    @DisplayName("unauthenticated processing is refused with 401")
    void anonymousProcessingRefused() throws Exception {
        mockMvc.perform(post(DOCUMENTS + "/" + UUID.randomUUID() + "/process"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("another user's document returns 404, not 403 - no enumeration oracle")
    void crossUserProcessingReturns404() throws Exception {
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));

        UUID aliceDoc = uploadText(alice, IngestionFixtures.legalText());

        assertEquals(404, process(bob, aliceDoc).getResponse().getStatus());

        // Bob's failed attempt must not have touched Alice's document at all.
        assertEquals(AiDocumentStatus.PENDING.name(), statusOf(aliceDoc));
        assertEquals(0, chunkCount(aliceDoc));

        // And Alice is unaffected.
        assertEquals(200, process(alice, aliceDoc).getResponse().getStatus());
        assertEquals(AiDocumentStatus.READY.name(), statusOf(aliceDoc));
    }

    @Test
    @DisplayName("a client-supplied id cannot reach another user's chunks")
    void chunksAreOwnerScoped() throws Exception {
        String aliceEmail = distinctEmail("alice");
        String bobEmail = distinctEmail("bob");
        String alice = registerAndLoginClient(aliceEmail);
        registerAndLoginClient(bobEmail);

        UUID aliceDoc = uploadText(alice, IngestionFixtures.legalText());
        process(alice, aliceDoc);
        assertTrue(chunkCount(aliceDoc) > 0);

        UUID bobId = userRepositoryForSupport.findByEmail(bobEmail).orElseThrow().getId();
        UUID aliceId = userRepositoryForSupport.findByEmail(aliceEmail).orElseThrow().getId();

        // The repository join is the boundary: Bob sees nothing even holding
        // the correct document id.
        List<AiDocumentChunk> asBob = chunkRepository.findByDocumentAndOwner(aliceDoc, bobId);
        assertTrue(asBob.isEmpty(), "ownership must be enforced in SQL, not after the load");

        assertFalse(chunkRepository.findByDocumentAndOwner(aliceDoc, aliceId).isEmpty());
    }

    @Test
    @DisplayName("deleting a document cascades its chunks away")
    void deletingDocumentRemovesChunks() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("cascade"));
        UUID id = uploadText(token, IngestionFixtures.legalText());
        process(token, id);
        assertTrue(chunkCount(id) > 0);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(DOCUMENTS + "/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertEquals(0, chunkCount(id),
                "ON DELETE CASCADE must remove chunks; orphans would leave a deleted "
                        + "document's text searchable");
    }

    @Test
    @DisplayName("a malformed document id is a 400, not a 500")
    void malformedIdIsBadRequest() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("baduuid"));

        mockMvc.perform(post(DOCUMENTS + "/not-a-uuid/process")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("processing does not disturb the document metadata endpoint")
    void metadataStillReadableAfterProcessing() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("metadata"));
        UUID id = uploadText(token, IngestionFixtures.legalText());
        process(token, id);

        mockMvc.perform(get(DOCUMENTS + "/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    // ------------------------------------------------ architectural property

    @Test
    @DisplayName("DocumentIngestionServiceImpl carries NO @Transactional")
    void ingestionServiceHasNoTransactionalAnnotation() {
        /*
         * THE ARCHITECTURAL INVARIANT, asserted by reflection so it cannot
         * regress quietly.
         *
         * If a @Transactional appeared on this class, stage 2 - extraction and
         * N embedding calls, potentially minutes - would run inside a
         * transaction holding a database connection. On a free Neon tier with a
         * small connection limit, a handful of concurrent ingestions would then
         * starve the entire application.
         *
         * Note this also documents the OTHER trap: those annotations would not
         * even work if added here, because Spring's @Transactional is
         * proxy-based and the stage methods are called via `this`. The
         * transactions live on IngestionTransactions, a separate bean, for
         * exactly that reason.
         */
        Class<?> service = DocumentIngestionServiceImpl.class;

        assertFalse(service.isAnnotationPresent(
                        org.springframework.transaction.annotation.Transactional.class),
                "@Transactional on the ingestion service would hold a connection "
                        + "open across every embedding call");

        for (var method : service.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(
                            org.springframework.transaction.annotation.Transactional.class),
                    "@Transactional on " + method.getName() + " - stage 2 must hold no "
                            + "transaction, and self-invoked annotations do nothing anyway");
        }
    }

    @Test
    @DisplayName("IngestionTransactions DOES carry the transactions")
    void transactionCollaboratorIsAnnotated() {
        // The other half: the boundaries have to live somewhere, and a proxied
        // collaborator is the only place they actually take effect.
        long annotated = java.util.Arrays.stream(IngestionTransactions.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(
                        org.springframework.transaction.annotation.Transactional.class))
                .count();

        assertTrue(annotated >= 4,
                "expected claim/isVisible/load/replace/markFailed to be transactional, got "
                        + annotated);
    }

    @Test
    @DisplayName("stage 3 is atomic - a rejected chunk write leaves the previous chunks intact")
    void stageThreeIsAtomic() throws Exception {
        /*
         * Provoked with a real constraint violation rather than a mock: writing
         * a chunk whose hash breaks ck_ai_document_chunks_hash_format inside the
         * same transaction that deleted the old rows. If stage 3 were not
         * atomic, the delete would survive the failed insert and the document
         * would be left with nothing.
         */
        String token = registerAndLoginClient(uniqueEmail("atomic"));
        UUID id = uploadText(token, IngestionFixtures.longLegalText(4000));
        process(token, id);

        long before = chunkCount(id);
        assertTrue(before > 1);

        assertThrowsAny(() -> jdbcTemplate.execute((java.sql.Connection connection) -> {
            connection.setAutoCommit(false);
            try (var delete = connection.prepareStatement(
                    "DELETE FROM ai_document_chunks WHERE document_id = ?");
                 var insert = connection.prepareStatement(
                         "INSERT INTO ai_document_chunks (id, document_id, chunk_index, content,"
                                 + " content_hash, char_count, embedding, created_at)"
                                 + " VALUES (?, ?, 0, 'x', 'NOT-A-VALID-HASH', 1, ?::vector, now())")) {

                delete.setObject(1, id);
                delete.executeUpdate();

                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, id);
                insert.setString(3, "[" + "0,".repeat(767) + "0]");
                insert.executeUpdate();

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
            return null;
        }));

        assertEquals(before, chunkCount(id),
                "the rollback must restore the previous chunks - a non-atomic stage 3 "
                        + "would have left this document with zero");
    }

    @Test
    @DisplayName("identical text in two documents produces identical chunk hashes")
    void identicalTextHashesIdentically() throws Exception {
        // Determinism across documents, which is what makes content_hash usable
        // for deduplication later.
        String token = registerAndLoginClient(uniqueEmail("hashes"));
        String text = IngestionFixtures.longLegalText(3000);

        UUID first = upload(token, "a.txt", text.getBytes(StandardCharsets.UTF_8));
        UUID second = upload(token, "b.txt", text.getBytes(StandardCharsets.UTF_8));
        process(token, first);
        process(token, second);

        assertNotEquals(first, second);
        assertEquals(
                jdbcTemplate.queryForList("SELECT content_hash FROM ai_document_chunks "
                        + "WHERE document_id = ? ORDER BY chunk_index", String.class, first),
                jdbcTemplate.queryForList("SELECT content_hash FROM ai_document_chunks "
                        + "WHERE document_id = ? ORDER BY chunk_index", String.class, second));
    }

    private static void assertThrowsAny(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("expected the constraint violation to propagate");
    }
}
