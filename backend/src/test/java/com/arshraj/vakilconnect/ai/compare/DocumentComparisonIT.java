package com.arshraj.vakilconnect.ai.compare;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/ai/documents/compare} end to end: real HTTP, real security
 * chain, real Testcontainers PostgreSQL, real AI-1 upload and AI-2 ingestion -
 * with a scripted model, for the same reason AI-4's DocumentAnalysisIT scripts
 * one: the stub returns an unmistakable non-JSON placeholder, so a happy path
 * requires a fake that actually emits the required schema.
 */
@DisplayName("Document comparison endpoint")
@Import(DocumentComparisonIT.ScriptedModelConfig.class)
class DocumentComparisonIT extends AbstractIntegrationTest {

    private static final String DOCUMENTS = "/api/ai/documents";
    private static final String COMPARE = DOCUMENTS + "/compare";

    private static final String FIRST_TEXT =
            "Lease Agreement dated 1 April 2026. Clause 3. Rent is Rs 25,000 "
                    + "per month, payable on the fifth day.";
    private static final String SECOND_TEXT =
            "Lease Agreement dated 1 April 2026. Clause 3. Rent is Rs 30,000 "
                    + "per month, payable on the first day.";

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
        model = (ScriptedLlmClient) llmClient;
        model.reset();
    }

    private UUID seedReady(String token, String filename, String text) throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart(DOCUMENTS)
                        .file(new MockMultipartFile("file", filename, "text/plain",
                                text.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = UUID.fromString(objectMapper
                .readTree(uploaded.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post(DOCUMENTS + "/" + id + "/process")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        return id;
    }

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

    private MvcResult compare(String token, Object firstId, Object secondId) throws Exception {
        return mockMvc.perform(post(COMPARE)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documentId", String.valueOf(firstId),
                                "compareToDocumentId", String.valueOf(secondId)))))
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------- security

    @Test
    @DisplayName("an unauthenticated compare is refused with 401")
    void anonymousCompareRefused() throws Exception {
        mockMvc.perform(post(COMPARE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documentId", UUID.randomUUID().toString(),
                                "compareToDocumentId", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());

        assertEquals(0, model.callCount());
    }

    @Test
    @DisplayName("ANOTHER USER'S DOCUMENT ON EITHER SIDE IS 404, and the model is never called")
    void anotherUsersDocumentIsNotFound() throws Exception {
        String alice = registerAndLoginClient(distinctEmail("alice"));
        String bob = registerAndLoginClient(distinctEmail("bob"));

        UUID aliceDoc = seedReady(alice, "alice-lease.txt", FIRST_TEXT);
        UUID bobDoc = seedReady(bob, "bob-lease.txt", SECOND_TEXT);
        model.reset();

        MvcResult result = compare(bob, aliceDoc, bobDoc);

        assertEquals(404, result.getResponse().getStatus());
        assertEquals(0, model.callCount(), "another user's document reached the model");

        String raw = result.getResponse().getContentAsString();
        assertFalse(raw.contains("alice-lease.txt"));
        assertFalse(raw.contains("25,000"));
    }

    @Test
    @DisplayName("an unknown document id on either side is the same 404")
    void unknownDocumentIsNotFound() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID owned = seedReady(token, "lease.txt", FIRST_TEXT);

        assertEquals(404,
                compare(token, owned, UUID.randomUUID()).getResponse().getStatus());
        assertEquals(0, model.callCount());
    }

    @Test
    @DisplayName("comparing a document with itself is a 400, before any lookup")
    void sameDocumentIsBadRequest() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID document = seedReady(token, "lease.txt", FIRST_TEXT);
        model.reset();

        MvcResult result = compare(token, document, document);

        assertEquals(400, result.getResponse().getStatus());
        assertEquals(0, model.callCount());
    }

    @Test
    @DisplayName("a non-UUID id is a 400 before the service is reached")
    void malformedRequestIsBadRequest() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));

        MvcResult result = mockMvc.perform(post(COMPARE)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documentId", "not-a-uuid",
                                "compareToDocumentId", UUID.randomUUID().toString()))))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
    }

    // -------------------------------------------------------- state guards

    @Test
    @DisplayName("an UNPROCESSED document on either side is 409 DOCUMENT_NOT_ANALYZABLE")
    void unprocessedDocumentIsRejected() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID ready = seedReady(token, "lease-a.txt", FIRST_TEXT);
        UUID pending = upload(token, "lease-b.txt", SECOND_TEXT);
        model.reset();

        MvcResult result = compare(token, ready, pending);

        assertEquals(409, result.getResponse().getStatus());
        assertEquals(DocumentNotAnalyzableException.CODE, bodyOf(result).get("code").asText());
        assertEquals(0, model.callCount());
    }

    // ------------------------------------------------------------ happy path

    @Test
    @DisplayName("two processed documents return the full structured contract")
    void comparesTwoProcessedDocuments() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "lease-a.txt", FIRST_TEXT);
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset();

        MvcResult result = compare(token, first, second);

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = bodyOf(result);

        assertEquals(first.toString(), body.get("firstDocumentId").asText());
        assertEquals("lease-a.txt", body.get("firstDocumentName").asText());
        assertEquals(second.toString(), body.get("secondDocumentId").asText());
        assertEquals("lease-b.txt", body.get("secondDocumentName").asText());

        for (String field : List.of("keyDifferences", "onlyInFirst", "onlyInSecond")) {
            assertTrue(body.get(field).isArray(), field + " must be a JSON array");
        }
        assertFalse(body.get("truncated").asBoolean());
    }

    @Test
    @DisplayName("the response is a STRUCTURED DTO - no raw model blob, no provider detail")
    void responseIsStructuredAndLeaksNothing() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "lease-a.txt", FIRST_TEXT);
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset();

        MvcResult result = compare(token, first, second);
        String raw = result.getResponse().getContentAsString();

        assertFalse(raw.contains("scripted"), "the provider name leaked into the response");
        assertFalse(raw.toLowerCase().contains("ollama"));
    }

    // -------------------------------------------------- untrusted model output

    @Test
    @DisplayName("THE MODEL CANNOT CHANGE EITHER DOCUMENT'S IDENTITY over HTTP")
    void modelSuppliedIdentityIsIgnored() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "lease-a.txt", FIRST_TEXT);
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset().replying(ComparisonFixtures.replyClaimingIdentity());

        MvcResult result = compare(token, first, second);
        String raw = result.getResponse().getContentAsString();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(first.toString(), bodyOf(result).get("firstDocumentId").asText());
        assertEquals("lease-a.txt", bodyOf(result).get("firstDocumentName").asText());
        assertFalse(raw.contains(ComparisonFixtures.FORGED_ID.toString()));
        assertFalse(raw.contains("attacker-owned.pdf"));
        assertFalse(raw.contains("grantedAccess"));
    }

    @Test
    @DisplayName("a document containing an injection is compared as data, identity unchanged")
    void injectedDocumentCannotChangeTheResponse() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "hostile.txt", ComparisonFixtures.MALICIOUS_CLAUSE);
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset().replying(ComparisonFixtures.replyClaimingIdentity());

        MvcResult result = compare(token, first, second);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(first.toString(), bodyOf(result).get("firstDocumentId").asText());
        assertEquals("hostile.txt", bodyOf(result).get("firstDocumentName").asText());
    }

    @Test
    @DisplayName("MALFORMED model output is a 503 with a fixed message")
    void malformedModelOutputIsServiceUnavailable() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "lease-a.txt", FIRST_TEXT);
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset().replying("Sure! Here is your comparison.");

        MvcResult result = compare(token, first, second);

        assertEquals(503, result.getResponse().getStatus());
        assertEquals("The document assistant is temporarily unavailable. Please try again.",
                bodyOf(result).get("message").asText());
    }

    @Test
    @DisplayName("an UNREACHABLE model is a 503 that names no host")
    void modelFailureIsServiceUnavailable() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "lease-a.txt", FIRST_TEXT);
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset().failingWith(new LlmException("connection refused to localhost:11434"));

        MvcResult result = compare(token, first, second);

        assertEquals(503, result.getResponse().getStatus());
        assertFalse(result.getResponse().getContentAsString().contains("11434"));
    }

    // ------------------------------------------------------------- bounding

    @Test
    @DisplayName("a document over the per-document budget reports truncated=true")
    void oversizedDocumentReportsTruncation() throws Exception {
        // The test profile pins max-context-characters-per-document to 1500.
        StringBuilder longDocument = new StringBuilder();
        for (int clause = 1; longDocument.length() < 4000; clause++) {
            longDocument.append("Clause ").append(clause)
                    .append(". The party of the ").append(clause)
                    .append("th part shall perform obligation number ").append(clause)
                    .append(" within ").append(clause * 3)
                    .append(" days of the commencement date. ");
        }

        String token = registerAndLoginClient(uniqueEmail("client"));
        UUID first = seedReady(token, "long-contract.txt", longDocument.toString());
        UUID second = seedReady(token, "lease-b.txt", SECOND_TEXT);
        model.reset();

        MvcResult result = compare(token, first, second);

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(bodyOf(result).get("truncated").asBoolean());
    }
}
