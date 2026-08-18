package com.arshraj.vakilconnect.ai.document;

import com.arshraj.vakilconnect.ai.document.entity.AiDocument;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentRepository;
import com.arshraj.vakilconnect.common.exception.DocumentTooLargeException;
import com.arshraj.vakilconnect.common.exception.EmptyDocumentException;
import com.arshraj.vakilconnect.common.exception.UnsupportedDocumentTypeException;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The document API end to end, against a real PostgreSQL container.
 *
 * WHY AN INTEGRATION TEST AND NOT A SLICE. Three of the properties this feature
 * has to guarantee are not observable in a unit test:
 *
 *   * the entity validates against the V8 schema (`ddl-auto: validate` refuses
 *     to start otherwise, so context startup IS the assertion);
 *   * `byte[]` lands in a `bytea` column rather than a PostgreSQL large object,
 *     which is the @Lob trap and is asserted directly below against
 *     information_schema;
 *   * cross-user isolation holds through the real security filter chain and the
 *     real SQL, not through a mocked service.
 *
 * The pure string and byte rules live in DocumentFilenameSanitizerTest and
 * DocumentContentTypeDetectorTest, where they cost microseconds rather than
 * seconds each.
 */
@DisplayName("AI document API")
class AiDocumentIT extends AbstractIntegrationTest {

    private static final String DOCUMENTS = "/api/ai/documents";

    @Autowired
    private AiDocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------- helpers

    private MvcResult upload(String token, String filename, String declaredType, byte[] content)
            throws Exception {

        MockMultipartFile part = new MockMultipartFile("file", filename, declaredType, content);

        return mockMvc.perform(multipart(DOCUMENTS)
                        .file(part)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private UUID uploadExpectingSuccess(String token, String filename,
                                        String declaredType, byte[] content) throws Exception {

        MvcResult result = upload(token, filename, declaredType, content);
        assertEquals(201, result.getResponse().getStatus(),
                "expected 201, body was: " + result.getResponse().getContentAsString());

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    // --------------------------------------------------------- happy paths

    @Test
    @DisplayName("uploading a PDF succeeds and lands in PENDING")
    void uploadPdf() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("pdfuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "contract.pdf",
                                "application/pdf", DocumentFixtures.pdf()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value("contract.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(DocumentFixtures.pdf().length))
                // 64 hex characters, matching ck_ai_documents_sha256_format.
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.matchesPattern(
                        "^[0-9a-f]{64}$")))
                // PENDING, not READY: nothing has extracted any text.
                .andExpect(jsonPath("$.status").value(AiDocumentStatus.PENDING.name()));
    }

    @Test
    @DisplayName("uploading a DOCX succeeds")
    void uploadDocx() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("docxuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "agreement.docx",
                                "application/octet-stream", DocumentFixtures.docx()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                // The DETECTED type is stored. The client said
                // application/octet-stream and was ignored.
                .andExpect(jsonPath("$.contentType").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    @DisplayName("uploading a TXT succeeds")
    void uploadTxt() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("txtuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "notes.txt",
                                "text/plain", DocumentFixtures.txt()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("text/plain"));
    }

    // ---------------------------------------------------------- rejections

    @Test
    @DisplayName("an unsupported extension is rejected with 415")
    void unsupportedExtensionRejected() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("exeuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "malware.exe",
                                "application/pdf", DocumentFixtures.pdf()))
                        .header("Authorization", bearer(token)))
                // 415, not 400: the request was understood; the payload format
                // is what is declined.
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(UnsupportedDocumentTypeException.CODE));
    }

    @Test
    @DisplayName("a file whose BYTES disagree with its extension is rejected")
    void spoofedContentRejected() throws Exception {
        /*
         * THE TEST THAT PROVES THE CLIENT IS NOT TRUSTED. The extension says
         * .pdf, the multipart header says application/pdf, and both are chosen
         * by the caller. The bytes are an ELF binary. Only the bytes are
         * evidence, and they are what gets this rejected.
         */
        String token = registerAndLoginClient(uniqueEmail("spoofuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "invoice.pdf",
                                "application/pdf", DocumentFixtures.binaryWithNulBytes()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(UnsupportedDocumentTypeException.CODE));
    }

    @Test
    @DisplayName("a ZIP renamed to .docx is rejected")
    void zipRenamedToDocxRejected() throws Exception {
        // Same magic number as a real DOCX; no word/document.xml inside.
        String token = registerAndLoginClient(uniqueEmail("zipuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "sheet.docx",
                                "application/octet-stream", DocumentFixtures.zipThatIsNotDocx()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("an oversized file is rejected with 413")
    void oversizedRejected() throws Exception {
        // application-test.yaml pins the limit to 64KB so this needs ~100KB
        // rather than 11MB. The container limit stays at 12MB, so the
        // application's own check is the one that fires - the path production
        // uses.
        String token = registerAndLoginClient(uniqueEmail("biguser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "huge.txt", "text/plain",
                                DocumentFixtures.oversizedText(100_000)))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(DocumentTooLargeException.CODE));
    }

    @Test
    @DisplayName("an empty file is rejected with 400")
    void emptyRejected() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("emptyuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "empty.txt",
                                "text/plain", new byte[0]))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(EmptyDocumentException.CODE));
    }

    @Test
    @DisplayName("a missing file part is a described 400, not a 500")
    void missingPartRejected() throws Exception {
        // The part is declared required=false precisely so this produces an
        // error code the client can act on instead of Spring's
        // MissingServletRequestPartException becoming a 500.
        String token = registerAndLoginClient(uniqueEmail("nopartuser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(EmptyDocumentException.CODE));
    }

    @Test
    @DisplayName("a path-traversal filename is stored sanitised, not rejected")
    void pathTraversalIsSanitised() throws Exception {
        /*
         * Cleaned rather than refused: the user's file is perfectly valid and
         * only its NAME was hostile. The response echoes the sanitised name, so
         * the caller learns immediately rather than being surprised by it in
         * the list later.
         */
        String token = registerAndLoginClient(uniqueEmail("traversaluser"));

        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "../../../etc/passwd.txt",
                                "text/plain", DocumentFixtures.txt()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("passwd.txt"));
    }

    // -------------------------------------------------------- authentication

    @Test
    @DisplayName("an anonymous upload is refused by the filter chain")
    void anonymousUploadRefused() throws Exception {
        // No new SecurityConfig matcher was added for /api/ai/**; this asserts
        // that anyRequest().authenticated() already covers it.
        mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", "x.txt",
                                "text/plain", DocumentFixtures.txt())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anonymous list and get are refused")
    void anonymousReadsRefused() throws Exception {
        mockMvc.perform(get(DOCUMENTS))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(DOCUMENTS + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- listing

    @Test
    @DisplayName("a user lists their own documents, newest first, and nobody else's")
    void listsOwnDocumentsOnly() throws Exception {
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));

        uploadExpectingSuccess(alice, "first.txt", "text/plain",
                DocumentFixtures.txt("first"));
        uploadExpectingSuccess(alice, "second.pdf", "application/pdf",
                DocumentFixtures.pdf());
        uploadExpectingSuccess(bob, "bobs.txt", "text/plain",
                DocumentFixtures.txt("bob's private document"));

        mockMvc.perform(get(DOCUMENTS).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // DESC by createdAt: the most recent upload is first.
                .andExpect(jsonPath("$[0].filename").value("second.pdf"))
                .andExpect(jsonPath("$[1].filename").value("first.txt"));

        mockMvc.perform(get(DOCUMENTS).header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("bobs.txt"));
    }

    // --------------------------------------------------- cross-user access

    @Test
    @DisplayName("a user CANNOT read another user's document — 404, not 403")
    void cannotReadAnotherUsersDocument() throws Exception {
        /*
         * 404 IS THE DELIBERATE ANSWER. A 403 would confirm the id exists,
         * turning this endpoint into an oracle that maps out what other users
         * hold. The caller is entitled to identical information either way:
         * "there is no such document, as far as you are concerned".
         */
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));

        UUID aliceDoc = uploadExpectingSuccess(alice, "private.txt", "text/plain",
                DocumentFixtures.txt("alice's confidential settlement terms"));

        mockMvc.perform(get(DOCUMENTS + "/" + aliceDoc)
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        // And the owner is unaffected.
        mockMvc.perform(get(DOCUMENTS + "/" + aliceDoc)
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("private.txt"));
    }

    @Test
    @DisplayName("a user CANNOT delete another user's document, and it survives")
    void cannotDeleteAnotherUsersDocument() throws Exception {
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));

        UUID aliceDoc = uploadExpectingSuccess(alice, "keepme.txt", "text/plain",
                DocumentFixtures.txt("do not delete"));

        mockMvc.perform(delete(DOCUMENTS + "/" + aliceDoc)
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        // THE ASSERTION THAT MATTERS. A 404 response would be worthless if the
        // row had been deleted anyway.
        assertTrue(documentRepository.findById(aliceDoc).isPresent(),
                "Bob's failed delete must not have removed Alice's document");

        mockMvc.perform(get(DOCUMENTS + "/" + aliceDoc)
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------- delete

    @Test
    @DisplayName("deleting your own document works and it is gone")
    void deletesOwnDocument() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("deleter"));

        UUID id = uploadExpectingSuccess(token, "temp.txt", "text/plain",
                DocumentFixtures.txt("temporary"));

        mockMvc.perform(delete(DOCUMENTS + "/" + id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertTrue(documentRepository.findById(id).isEmpty(),
                "the row must actually be gone, not merely reported as deleted");

        mockMvc.perform(get(DOCUMENTS + "/" + id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting a document that does not exist is 404, not a silent 204")
    void deleteMissingIsNotFound() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("ghostdeleter"));

        mockMvc.perform(delete(DOCUMENTS + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------ no bytes leaked

    @Test
    @DisplayName("NO response ever contains the document bytes")
    void responsesNeverCarryContent() throws Exception {
        /*
         * Asserted on the RAW RESPONSE STRING, not just on absent JSON fields.
         * A missing `content` property proves the DTO has no such component; it
         * would not catch bytes arriving under some other name, or a base64
         * blob appended by a serialiser someone configured. Searching the whole
         * body for a distinctive marker from inside the file catches all of
         * those at once.
         */
        String marker = "SETTLEMENT-AMOUNT-4700000-RUPEES";
        String token = registerAndLoginClient(uniqueEmail("privacyuser"));

        MvcResult created = upload(token, "settlement.txt", "text/plain",
                DocumentFixtures.txt("Confidential. " + marker + " payable on signing."));
        assertEquals(201, created.getResponse().getStatus());

        String uploadBody = created.getResponse().getContentAsString();
        assertFalse(uploadBody.contains(marker),
                "the upload response echoed document content");
        /*
         * `"content":` WITH THE COLON, not the bare word. The response
         * legitimately contains `"contentType"`, which has "content" as a
         * substring - a naive containment check here fails on a correct
         * response, which is exactly the kind of test that gets "fixed" by
         * deleting the assertion.
         */
        assertFalse(uploadBody.contains("\"content\":"),
                "the upload response has a content field");

        UUID id = UUID.fromString(objectMapper.readTree(uploadBody).get("id").asText());

        String metadataBody = mockMvc.perform(get(DOCUMENTS + "/" + id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(metadataBody.contains(marker),
                "the metadata response leaked document content");

        String listBody = mockMvc.perform(get(DOCUMENTS)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(listBody.contains(marker),
                "the list response leaked document content");
    }

    @Test
    @DisplayName("failureReason is omitted entirely while a document is healthy")
    void failureReasonOmittedWhenNull() throws Exception {
        // @JsonInclude(NON_NULL), matching ErrorResponse.code. A permanent
        // "failureReason": null on every healthy document invites a client to
        // branch on it.
        String token = registerAndLoginClient(uniqueEmail("healthyuser"));
        UUID id = uploadExpectingSuccess(token, "fine.txt", "text/plain",
                DocumentFixtures.txt("all good"));

        mockMvc.perform(get(DOCUMENTS + "/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    // ------------------------------------------------------ storage mapping

    @Test
    @DisplayName("content is stored in a `bytea` column, NOT a large object")
    void contentColumnIsBytea() {
        /*
         * THE @Lob TRAP, ASSERTED DIRECTLY.
         *
         * Hibernate maps a plain byte[] to VARBINARY (PostgreSQL `bytea`) and
         * an @Lob byte[] to BLOB (PostgreSQL `oid` - a large-object REFERENCE,
         * with the bytes living in pg_largeobject and leaking forever unless
         * something calls lo_unlink). Both mappings validate against a schema;
         * they simply store the data in different places.
         *
         * If someone "tidies" AiDocument by adding @Lob, or removes the
         * explicit @JdbcTypeCode, every other test in this class still passes -
         * uploads work, reads work - while documents quietly accumulate as
         * orphaned large objects. This is the only assertion that catches it.
         */
        String dataType = jdbcTemplate.queryForObject("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_name = 'ai_documents'
                   AND column_name = 'content'
                """, String.class);

        assertEquals("bytea", dataType,
                "content must be bytea; 'oid' means someone added @Lob and the bytes "
                        + "are now orphaned large objects");
    }

    @Test
    @DisplayName("the stored bytes are byte-identical to what was uploaded")
    void bytesRoundTrip() throws Exception {
        // Proves the bytea mapping actually works rather than merely being
        // declared, and that nothing re-encoded the payload on the way in.
        String token = registerAndLoginClient(uniqueEmail("roundtrip"));
        byte[] original = DocumentFixtures.docx();

        UUID id = uploadExpectingSuccess(token, "roundtrip.docx",
                "application/octet-stream", original);

        AiDocument stored = documentRepository.findById(id).orElseThrow();

        assertArrayEquals(original, stored.getContent());
        assertEquals(original.length, stored.getSizeBytes());
        assertEquals(AiDocumentStatus.PENDING, stored.getStatus());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
    }

    @Test
    @DisplayName("the entity's toString never renders the bytes")
    void entityToStringIsSafe() throws Exception {
        // A byte[] in a generated toString is either useless (array identity)
        // or catastrophic (a decimal dump of a legal document into the logs).
        String token = registerAndLoginClient(uniqueEmail("tostring"));
        UUID id = uploadExpectingSuccess(token, "secret.txt", "text/plain",
                DocumentFixtures.txt("MARKER-DO-NOT-LOG"));

        String rendered = documentRepository.findById(id).orElseThrow().toString();

        assertFalse(rendered.contains("MARKER-DO-NOT-LOG"));
        assertTrue(rendered.contains("not shown"));
        assertTrue(rendered.contains("secret.txt"));
    }

    // --------------------------------------------------------- misc routing

    @Test
    @DisplayName("a non-UUID document id is a 400, not a 500")
    void malformedIdIsBadRequest() throws Exception {
        // Handled by the existing MethodArgumentTypeMismatchException handler,
        // so a malformed id never reaches the service.
        String token = registerAndLoginClient(uniqueEmail("baduuid"));

        mockMvc.perform(get(DOCUMENTS + "/not-a-uuid")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a new user's list is empty, not an error")
    void emptyListForNewUser() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("newbie"));

        mockMvc.perform(get(DOCUMENTS).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("countByUserId counts only that user's documents")
    void countIsScopedToOwner() throws Exception {
        String aliceEmail = distinctEmail("alice");
        String bobEmail = distinctEmail("bob");
        String alice = registerAndLoginClient(aliceEmail);
        String bob = registerAndLoginClient(bobEmail);

        uploadExpectingSuccess(alice, "a.txt", "text/plain", DocumentFixtures.txt("a"));
        uploadExpectingSuccess(bob, "b.txt", "text/plain", DocumentFixtures.txt("b"));

        /*
         * The owner id comes from the USER repository, not from
         * document.getUser().getId().
         *
         * AiDocument.user is LAZY and `open-in-view: false`, so touching the
         * proxy out here - outside any transaction - is a
         * LazyInitializationException, not a value. That is the mapping working
         * as intended; the test just has to ask the right object.
         */
        UUID aliceId = userRepositoryForSupport.findByEmail(aliceEmail).orElseThrow().getId();
        UUID bobId = userRepositoryForSupport.findByEmail(bobEmail).orElseThrow().getId();

        assertEquals(1, documentRepository.countByUserId(aliceId));
        assertEquals(1, documentRepository.countByUserId(bobId));
    }

    @Test
    @DisplayName("uploads by different users with identical content both succeed")
    void identicalContentIsNotDeduplicated() throws Exception {
        /*
         * Guards the decision recorded in V8: ix_ai_documents_user_sha256 is
         * NOT unique. Two users uploading the same standard lease template must
         * both get their own row - a unique index would hand the second one a
         * 409 for a file they are entitled to store.
         */
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));
        byte[] sameFile = DocumentFixtures.txt("standard lease template");

        UUID aliceDoc = uploadExpectingSuccess(alice, "lease.txt", "text/plain", sameFile);
        UUID bobDoc = uploadExpectingSuccess(bob, "lease.txt", "text/plain", sameFile);

        assertFalse(aliceDoc.equals(bobDoc));
        assertEquals(
                documentRepository.findById(aliceDoc).orElseThrow().getSha256(),
                documentRepository.findById(bobDoc).orElseThrow().getSha256(),
                "identical bytes must hash identically - the hash is unsalted on purpose");
    }

    @Test
    @DisplayName("the same user may upload the same file twice — retries are not blocked")
    void sameUserMayReupload() throws Exception {
        // The other half of the non-unique index decision: a failed or deleted
        // upload must be retryable.
        String token = registerAndLoginClient(uniqueEmail("retrier"));
        byte[] file = DocumentFixtures.txt("retry me");

        UUID first = uploadExpectingSuccess(token, "retry.txt", "text/plain", file);
        UUID second = uploadExpectingSuccess(token, "retry.txt", "text/plain", file);

        assertFalse(first.equals(second), "each upload must be its own row");
        assertTrue(documentRepository.findById(first).isPresent());
        assertTrue(documentRepository.findById(second).isPresent());
    }
}
