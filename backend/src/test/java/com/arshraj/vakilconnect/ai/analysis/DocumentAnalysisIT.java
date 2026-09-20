package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.common.exception.DocumentNotAnalyzableException;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/ai/documents/{id}/analyze} end to end: real HTTP, real
 * security chain, real Testcontainers PostgreSQL, real AI-1 upload and AI-2
 * ingestion - with a SCRIPTED model.
 *
 * ================== WHY THE MODEL BEAN IS REPLACED HERE =====================
 *
 * The suite runs with vakilconnect.ai.provider=stub, and StubLlmClient
 * deliberately returns an unmistakable "[stub-llm] no model was called" string
 * rather than realistic output - which is right for AI-0 through AI-3, where
 * the answer is free text, and useless here, where the answer must be JSON
 * matching a schema. Analysing anything through the stub would produce a 503
 * every time, so the happy path could not be tested over HTTP at all.
 *
 * The alternative - teaching StubLlmClient to recognise the analysis operation
 * and emit matching JSON - was rejected. It would put AI-4's response schema
 * inside an AI-0 class, so the stub would have to be edited every time the
 * schema changed, and a stub that knows about one feature's contract is a stub
 * that will grow to know about all of them.
 *
 * Bean overriding is enabled for the test profile only (see
 * application-test.yaml), and EmailDispatchIT already establishes this exact
 * pattern for `emailTaskExecutor`. The cost is one extra Spring context in the
 * cache, which shares the same container and the same already-migrated schema.
 *
 * STILL NO INFERENCE SERVER, ANYWHERE. The scripted client is an in-process
 * object; nothing in this class can reach localhost:11434 or any network.
 * AiFoundationIT separately proves that the DEFAULT context - the one every
 * other test uses - resolves StubLlmClient and contains no Ollama adapter at
 * all.
 */
@DisplayName("Document analysis endpoint")
@Import(DocumentAnalysisIT.ScriptedModelConfig.class)
class DocumentAnalysisIT extends AbstractIntegrationTest {

    private static final String DOCUMENTS = "/api/ai/documents";

    private static final String CONTRACT = """
            Rental Agreement dated 1 April 2026 between Ramesh Kumar (Landlord) \
            and Anita Sharma (Tenant). Clause 3. The tenant shall pay rent of \
            Rs 25,000 on the fifth day of each month. Clause 7. Either party \
            may terminate this agreement on thirty days written notice.""";

    /**
     * Replaces the model for this context only.
     *
     * @Primary because the test profile selects StubLlmClient, so two LlmClient
     * beans are present; without it, every constructor injecting the interface
     * would fail on an ambiguous candidate.
     *
     * A @TestConfiguration rather than a @Component: Boot's TypeExcludeFilter
     * skips @TestConfiguration during component scanning, so this cannot leak
     * into the other contexts in the suite. EmailDispatchIT records what happens
     * when that rule is broken.
     */
    @TestConfiguration
    static class ScriptedModelConfig {
        @Bean
        @Primary
        LlmClient scriptedLlmClient() {
            return new ScriptedLlmClient();
        }
    }

    @Autowired
    private LlmClient llmClient;

    private ScriptedLlmClient model;

    @BeforeEach
    void resetTheModel() {
        // The context is cached across this class, so the fake carries state
        // between tests unless it is cleared. A leftover staged failure would
        // fail an unrelated test somewhere later in the class.
        model = (ScriptedLlmClient) llmClient;
        model.reset();
    }

    // ---------------------------------------------------------------- seeds

    /** Uploads a document and leaves it PENDING. */
    private UUID upload(String token, String filename, String text) throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", filename, "text/plain",
                                text.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper
                .readTree(uploaded.getResponse().getContentAsString()).get("id").asText());
    }

    /** Uploads AND ingests, so the document is READY with real chunks. */
    private UUID seedReady(String token, String filename, String text) throws Exception {
        UUID id = upload(token, filename, text);

        mockMvc.perform(post(DOCUMENTS + "/" + id + "/process")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        return id;
    }

    private MvcResult analyze(String token, Object documentId) throws Exception {
        return mockMvc.perform(post(DOCUMENTS + "/" + documentId + "/analyze")
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------- security

    @Test
    @DisplayName("an unauthenticated analyze is refused with 401")
    void anonymousAnalyzeRefused() throws Exception {
        mockMvc.perform(post(DOCUMENTS + "/" + UUID.randomUUID() + "/analyze"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, model.callCount(),
                "an anonymous request must be stopped by the filter chain, "
                        + "long before anything reaches the model");
    }

    @Test
    @DisplayName("ANOTHER USER'S DOCUMENT IS 404, not 403 - and its text is never read")
    void anotherUsersDocumentIsNotFound() throws Exception {
        /*
         * The anti-enumeration convention every route on this controller
         * follows. A 403 would confirm the document exists, turning this
         * endpoint into an oracle for what other users have uploaded.
         *
         * The call-count assertion is the second half: ownership is enforced in
         * the WHERE clause of the metadata read, so Bob's request never loads
         * Alice's chunks and never reaches the model.
         */
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));

        UUID aliceDocument = seedReady(alice, "alice-lease.txt", CONTRACT);
        model.reset();

        MvcResult result = analyze(bob, aliceDocument);

        assertEquals(404, result.getResponse().getStatus());
        assertEquals(0, model.callCount(), "another user's document reached the model");

        String raw = result.getResponse().getContentAsString();
        assertFalse(raw.contains("alice-lease.txt"), "another user's filename leaked");
        assertFalse(raw.contains("Ramesh"), "another user's document text leaked");
    }

    @Test
    @DisplayName("an unknown document id is the same 404")
    void unknownDocumentIsNotFound() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));

        assertEquals(404, analyze(token, UUID.randomUUID()).getResponse().getStatus());
        assertEquals(0, model.callCount());
    }

    @Test
    @DisplayName("a non-UUID document id is a 400 before the service is reached")
    void malformedDocumentIdIsBadRequest() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));

        assertEquals(400, analyze(token, "not-a-uuid").getResponse().getStatus());
    }

    // -------------------------------------------------------- state guards

    @Test
    @DisplayName("an UNPROCESSED document is 409 DOCUMENT_NOT_ANALYZABLE")
    void unprocessedDocumentIsRejected() throws Exception {
        /*
         * A distinct code from DOCUMENT_ALREADY_PROCESSING, because the client
         * action is distinct: call /process, rather than wait for it. Collapsing
         * them would leave a frontend polling forever on a document that will
         * never leave PENDING on its own.
         */
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID pending = upload(token, "lease.txt", CONTRACT);

        MvcResult result = analyze(token, pending);

        assertEquals(409, result.getResponse().getStatus());
        assertEquals(DocumentNotAnalyzableException.CODE, bodyOf(result).get("code").asText());
        assertEquals(DocumentNotAnalyzableException.NOT_PROCESSED,
                bodyOf(result).get("message").asText());
        assertEquals(0, model.callCount(),
                "an unprocessed document must not cost an inference call");
    }

    // ------------------------------------------------------------ happy path

    @Test
    @DisplayName("a processed document returns the full structured contract")
    void analysesAProcessedDocument() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "rental-agreement.txt", CONTRACT);
        model.reset();

        MvcResult result = analyze(token, document);

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = bodyOf(result);

        assertEquals(document.toString(), body.get("documentId").asText());
        assertEquals("rental-agreement.txt", body.get("documentName").asText());
        assertEquals("A residential tenancy agreement between two parties.",
                body.get("summary").asText());

        for (String field : List.of("parties", "importantDates", "obligations",
                "keyClauses", "risks")) {
            assertTrue(body.get(field).isArray(), field + " must be a JSON array");
        }
        assertEquals(2, body.get("parties").size());
        assertFalse(body.get("truncated").asBoolean());
    }

    @Test
    @DisplayName("the response is a STRUCTURED DTO - no raw model blob, no provider detail")
    void responseIsStructuredAndLeaksNothing() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "rental-agreement.txt", CONTRACT);
        model.reset();

        MvcResult result = analyze(token, document);
        JsonNode body = bodyOf(result);

        /*
         * The KEY SET, not the key order. Jackson's property ordering for
         * records is stable in practice but it is not a contract this project
         * has any reason to pin, and asserting it would turn a Jackson upgrade
         * into a failure that says nothing about the response being correct.
         * What matters is that there are no extra fields and none missing.
         */
        List<String> keys = new ArrayList<>();
        body.fieldNames().forEachRemaining(keys::add);
        keys.sort(String::compareTo);

        List<String> expected = new ArrayList<>(List.of("documentId", "documentName",
                "summary", "parties", "importantDates", "obligations", "keyClauses",
                "risks", "truncated"));
        expected.sort(String::compareTo);

        assertEquals(expected, keys, "the response shape must be exactly the declared DTO");

        String raw = result.getResponse().getContentAsString();
        assertFalse(raw.contains("scripted"), "the provider name leaked into the response");
        assertFalse(raw.toLowerCase().contains("ollama"));
        assertFalse(raw.toLowerCase().contains("llama3"));
    }

    // -------------------------------------------------- untrusted model output

    @Test
    @DisplayName("THE MODEL CANNOT CHANGE documentId OR documentName over HTTP")
    void modelSuppliedIdentityIsIgnored() throws Exception {
        /*
         * The end-to-end counterpart to the unit test. The model returns a reply
         * carrying documentId, documentName, userId and grantedAccess; the
         * response must still describe the document the DATABASE returned, and
         * the unknown fields must not appear at all.
         */
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "rental-agreement.txt", CONTRACT);
        model.reset().replying(AnalysisFixtures.replyClaimingIdentity());

        MvcResult result = analyze(token, document);
        String raw = result.getResponse().getContentAsString();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(document.toString(), bodyOf(result).get("documentId").asText());
        assertEquals("rental-agreement.txt", bodyOf(result).get("documentName").asText());

        assertFalse(raw.contains(AnalysisFixtures.FORGED_DOCUMENT_ID.toString()),
                "a model-supplied document id reached the response");
        assertFalse(raw.contains("attacker-owned.pdf"),
                "a model-supplied document name reached the response");
        assertFalse(raw.contains("grantedAccess"),
                "an unknown model field reached the response");
    }

    @Test
    @DisplayName("a document containing an INJECTION is analysed as data, identity unchanged")
    void injectedDocumentCannotChangeTheResponse() throws Exception {
        /*
         * The hostile text is uploaded as a real document, ingested by AI-2, and
         * fed to the model as the thing under analysis - which is correct, it IS
         * the document. What it cannot do is change which document the response
         * describes, because that comes from the database row.
         *
         * The scripted model plays along with the injection completely, which is
         * the worst case: even a fully compromised model changes nothing here.
         */
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "hostile.txt", AnalysisFixtures.MALICIOUS_CHUNK);
        model.reset().replying(AnalysisFixtures.replyClaimingIdentity());

        MvcResult result = analyze(token, document);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(document.toString(), bodyOf(result).get("documentId").asText());
        assertEquals("hostile.txt", bodyOf(result).get("documentName").asText());
        assertFalse(result.getResponse().getContentAsString()
                        .contains(AnalysisFixtures.FORGED_DOCUMENT_ID.toString()),
                "the injected document id reached the response");
    }

    @Test
    @DisplayName("MALFORMED model output is a 503 with a fixed message")
    void malformedModelOutputIsServiceUnavailable() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "rental-agreement.txt", CONTRACT);
        model.reset().replying("Sure! Here is your analysis of the tenancy agreement.");

        MvcResult result = analyze(token, document);

        assertEquals(503, result.getResponse().getStatus());

        String message = bodyOf(result).get("message").asText();
        assertEquals("The document assistant is temporarily unavailable. Please try again.",
                message);
        assertFalse(result.getResponse().getContentAsString().contains("tenancy agreement"),
                "the model's text was reflected back to the client");
    }

    @Test
    @DisplayName("an INCOMPLETE model reply is a 503, never a partly-filled analysis")
    void incompleteModelOutputIsServiceUnavailable() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "rental-agreement.txt", CONTRACT);
        model.reset().replying(AnalysisFixtures.replyWithout("risks"));

        assertEquals(503, analyze(token, document).getResponse().getStatus());
    }

    @Test
    @DisplayName("an UNREACHABLE model is a 503 that names no host")
    void modelFailureIsServiceUnavailable() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "rental-agreement.txt", CONTRACT);
        model.reset().failingWith(new LlmException("connection refused to localhost:11434"));

        MvcResult result = analyze(token, document);

        assertEquals(503, result.getResponse().getStatus());
        assertFalse(result.getResponse().getContentAsString().contains("11434"),
                "a provider internal reached the response body");
    }

    // ------------------------------------------------------------- bounding

    @Test
    @DisplayName("a document over the context budget reports truncated=true")
    void oversizedDocumentReportsTruncation() throws Exception {
        /*
         * The test profile pins vakilconnect.ai.analysis.max-context-characters
         * to 3000 so this needs a document of a few thousand characters rather
         * than the twelve thousand production would require - same branch,
         * a third of the stub embedding calls.
         *
         * The text is textually varied so AI-2's chunker produces distinct
         * chunks; identical filler would be collapsed by its no-duplicates rule
         * and there would be nothing to truncate.
         */
        StringBuilder longDocument = new StringBuilder();
        for (int clause = 1; longDocument.length() < 6000; clause++) {
            longDocument.append("Clause ").append(clause)
                    .append(". The party of the ").append(clause)
                    .append("th part shall perform obligation number ").append(clause)
                    .append(" within ").append(clause * 3)
                    .append(" days of the commencement date. ");
        }

        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "long-contract.txt", longDocument.toString());
        model.reset();

        MvcResult result = analyze(token, document);

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(bodyOf(result).get("truncated").asBoolean(),
                "a document over the context budget must report truncated=true");
    }
}
