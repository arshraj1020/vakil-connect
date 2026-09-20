package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.PermanentLlmException;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.arshraj.vakilconnect.common.exception.DocumentNotAnalyzableException;
import com.arshraj.vakilconnect.common.exception.DocumentProcessingConflictException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The analysis orchestration, with recording collaborators.
 *
 * A UNIT TEST, DELIBERATELY. The properties that matter most here are
 * statements about CONTROL FLOW - "the model was never called for a document in
 * this state", "the identity did not come from the model's output" - and a
 * recording fake proves them exactly. An integration test can only show the
 * outcome looked right, which is consistent with the model having been called
 * and its answer discarded.
 *
 * The real database path and the real HTTP path are covered by
 * DocumentAnalysisIT.
 */
@DisplayName("DocumentAnalysisServiceImpl")
class DocumentAnalysisServiceTest {

    private static final String EMAIL = "tenant@example.com";
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    /*
     * THE PUBLISHED METRIC CONTRACT, AS LITERALS.
     *
     * Micrometer renders `vakilconnect.ai.analysis.request` as
     * `vakilconnect_ai_analysis_request_total` at the Prometheus scrape, so
     * these exact strings are what a dashboard query and an alert rule are
     * written against. Referencing AnalysisMetrics' constants would make a
     * rename invisible here while silently breaking every dashboard; writing the
     * literals means a rename fails this test first and has to be a deliberate
     * decision about a public contract. Same reasoning as
     * OllamaEmbeddingClientTest.
     */
    private static final String ANALYSIS_COUNTER = "vakilconnect.ai.analysis.request";
    private static final String ANALYSIS_TIMER = "vakilconnect.ai.analysis.duration";

    private final AiAnalysisProperties properties = new AiAnalysisProperties(3000, 2000, 20, 400);

    private MeterRegistry registry;
    private ScriptedLlmClient llm;
    private RecordingLoader loader;
    private DocumentAnalysisServiceImpl service;

    /** Returns whatever the test stages, and records the owner it was asked for. */
    private static final class RecordingLoader implements AnalysisDocumentLoader {
        private AnalysisDocument result;
        private final List<UUID> owners = new ArrayList<>();
        private final List<UUID> documents = new ArrayList<>();

        @Override
        public Optional<AnalysisDocument> load(UUID documentId, UUID ownerId) {
            documents.add(documentId);
            owners.add(ownerId);
            return Optional.ofNullable(result);
        }
    }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        llm = new ScriptedLlmClient();
        loader = new RecordingLoader();

        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setEmail(EMAIL);

        /*
         * THE ONLY MOCK IN THIS CLASS, confined to one line - the same
         * concession RagServiceTest makes, for the same reason. UserRepository
         * extends JpaRepository, so hand-rolling it means stubbing dozens of
         * inherited methods to express "look up one user". Mockito ships with
         * spring-boot-starter-test, so this adds no dependency.
         */
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(owner));

        service = new DocumentAnalysisServiceImpl(users, loader,
                new AnalysisContextBuilder(properties), new AnalysisPromptBuilder(),
                new AnalysisJsonParser(properties), llm, new AnalysisMetrics(registry));
    }

    private double counter(String outcome) {
        var c = registry.find(ANALYSIS_COUNTER).tag("outcome", outcome).counter();
        return c == null ? 0d : c.count();
    }

    private DocumentAnalysis analyze() {
        return service.analyze(EMAIL, AnalysisFixtures.DOCUMENT_ID);
    }

    // ------------------------------------------------------------ happy path

    @Test
    @DisplayName("a READY document is analysed into every structured field")
    void analysesAReadyDocument() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);

        DocumentAnalysis analysis = analyze();

        assertEquals(AnalysisFixtures.DOCUMENT_ID, analysis.documentId());
        assertEquals(AnalysisFixtures.DOCUMENT_NAME, analysis.documentName());
        assertEquals("A residential tenancy agreement between two parties.", analysis.summary());
        assertEquals(2, analysis.parties().size());
        assertEquals(1, analysis.risks().size());
        assertFalse(analysis.truncated());

        assertEquals(1, llm.callCount(), "exactly one model call per analysis");
        assertEquals(1d, counter("success"));
        assertTrue(registry.find(ANALYSIS_TIMER).tag("outcome", "success").timer().count() > 0);
    }

    @Test
    @DisplayName("the owner passed to the loader is the RESOLVED user, and the id is the path's")
    void loaderIsCalledWithTheAuthenticatedOwner() {
        // The only two values the query is scoped by. Neither can be influenced
        // by a request body, because there is no request body.
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);

        analyze();

        assertEquals(List.of(OWNER_ID), loader.owners);
        assertEquals(List.of(AnalysisFixtures.DOCUMENT_ID), loader.documents);
    }

    // ------------------------------------------- THE IDENTITY GUARANTEE

    @Test
    @DisplayName("THE MODEL CANNOT CHANGE documentId OR documentName")
    void modelCannotChangeTheDocumentIdentity() {
        /*
         * THE SINGLE MOST IMPORTANT TEST IN THIS CLASS.
         *
         * The model returns a reply carrying documentId, documentName, userId
         * and grantedAccess - exactly what an injected document would ask it to
         * emit. The response must still describe the document the DATABASE
         * returned.
         *
         * This holds structurally rather than by a check: the parser produces an
         * AnalysisContent, which has no identity components, and the service
         * assembles the response from the loaded row. There is no code path from
         * model output to these two fields for a bug to be introduced into.
         */
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.MALICIOUS_CHUNK);
        llm.replying(AnalysisFixtures.replyClaimingIdentity());

        DocumentAnalysis analysis = analyze();

        assertEquals(AnalysisFixtures.DOCUMENT_ID, analysis.documentId(),
                "the model rewrote the document id");
        assertEquals(AnalysisFixtures.DOCUMENT_NAME, analysis.documentName(),
                "the model rewrote the document name");

        String rendered = analysis.summary() + analysis.parties() + analysis.risks();
        assertFalse(rendered.contains(AnalysisFixtures.FORGED_DOCUMENT_ID.toString()));
        assertFalse(rendered.contains("attacker-owned.pdf"));
    }

    @Test
    @DisplayName("a hostile document reaches the prompt as DATA and changes nothing else")
    void injectionAttemptIsContained() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.MALICIOUS_CHUNK);

        DocumentAnalysis analysis = analyze();

        LlmRequest request = llm.lastRequest();
        assertTrue(request.userPrompt().contains("Ignore all previous instructions"),
                "the document must be sent as-is - it is the thing being analysed");
        assertTrue(request.hasSystemPrompt(),
                "the rules must be a separate turn from the document");
        assertEquals(AnalysisFixtures.DOCUMENT_ID, analysis.documentId());
    }

    // --------------------------------------------------------- state checks

    @Test
    @DisplayName("a document that is not the caller's is a 404, and the model is NEVER called")
    void unknownOrForeignDocumentIsNotFound() {
        /*
         * The loader returns empty for both "does not exist" and "belongs to
         * somebody else" - indistinguishable by design. Asserting the CALL COUNT
         * is the point: refusing before generation means another user's id costs
         * nothing and reveals nothing, not even a timing difference.
         */
        loader.result = null;

        assertThrows(ResourceNotFoundException.class, this::analyze);

        assertEquals(0, llm.callCount(), "the model must not be called for a foreign document");
        assertEquals(0d, counter("success"));
    }

    @Test
    @DisplayName("a PENDING document is refused - process it first")
    void pendingDocumentIsRefused() {
        loader.result = AnalysisFixtures.inState(AiDocumentStatus.PENDING);

        DocumentNotAnalyzableException e =
                assertThrows(DocumentNotAnalyzableException.class, this::analyze);

        assertEquals(DocumentNotAnalyzableException.NOT_PROCESSED, e.getMessage());
        assertEquals(0, llm.callCount(), "an unprocessed document must not reach the model");
    }

    @Test
    @DisplayName("a FAILED document is refused with the same remedy")
    void failedDocumentIsRefused() {
        // Folded in with PENDING deliberately: the client action is identical,
        // and why the earlier run failed is already readable on GET /{id}.
        loader.result = AnalysisFixtures.inState(AiDocumentStatus.FAILED);

        DocumentNotAnalyzableException e =
                assertThrows(DocumentNotAnalyzableException.class, this::analyze);

        assertEquals(DocumentNotAnalyzableException.NOT_PROCESSED, e.getMessage());
        assertEquals(0, llm.callCount());
    }

    @Test
    @DisplayName("a PROCESSING document is a 409 CONFLICT - wait and poll")
    void processingDocumentIsAConflict() {
        // A different remedy from PENDING, so a different exception and a
        // different code: this one resolves itself, that one does not.
        loader.result = AnalysisFixtures.inState(AiDocumentStatus.PROCESSING);

        assertThrows(DocumentProcessingConflictException.class, this::analyze);
        assertEquals(0, llm.callCount());
    }

    @Test
    @DisplayName("a READY document with NO CHUNKS is refused rather than analysed empty")
    void readyDocumentWithoutTextIsRefused() {
        /*
         * Should be unreachable - ingestion marks a document FAILED rather than
         * READY when chunking yields nothing - so this covers a row left
         * inconsistent by an older run. Sending an empty context to the model
         * would produce a confident analysis of nothing, which is the worst
         * possible output.
         */
        loader.result = AnalysisFixtures.ready();

        DocumentNotAnalyzableException e =
                assertThrows(DocumentNotAnalyzableException.class, this::analyze);

        assertEquals(DocumentNotAnalyzableException.NO_TEXT, e.getMessage());
        assertEquals(0, llm.callCount());
    }

    // ------------------------------------------------------ model failures

    @Test
    @DisplayName("an unreachable model is a 503, counted as llm_failure")
    void modelFailureIsUnavailable() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);
        llm.failingWith(new LlmException("connection refused to localhost:11434"));

        AiAnswerUnavailableException e =
                assertThrows(AiAnswerUnavailableException.class, this::analyze);

        assertFalse(e.getMessage().contains("11434"),
                "the provider's host must not reach the domain exception's message");
        assertEquals(1d, counter("llm_failure"));
        assertEquals(0d, counter("invalid_output"));
    }

    @Test
    @DisplayName("a permanent model failure is handled the same way")
    void permanentModelFailureIsUnavailable() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);
        llm.failingWith(new PermanentLlmException("model llama3.2 not found"));

        assertThrows(AiAnswerUnavailableException.class, this::analyze);
        assertEquals(1d, counter("llm_failure"));
    }

    @Test
    @DisplayName("EMPTY model output is a failure, not an empty analysis")
    void emptyModelOutputIsAFailure() {
        /*
         * Returning an analysis with a blank summary and five empty lists would
         * be the worst possible response: it is structurally valid, it names a
         * real document, and it says nothing - a user would read it as "this
         * contract contains no obligations and no risks".
         *
         * The staged client throws rather than returning a blank LlmResponse
         * because LlmResponse refuses blank text by construction, which is how a
         * silent provider really surfaces.
         */
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);
        llm.replying("");

        assertThrows(AiAnswerUnavailableException.class, this::analyze);
        assertEquals(1d, counter("llm_failure"));
    }

    @Test
    @DisplayName("MALFORMED JSON is a 503, counted separately as invalid_output")
    void malformedJsonIsInvalidOutput() {
        /*
         * The two failures are counted apart because they mean different things
         * to an operator: llm_failure is infrastructure, invalid_output is the
         * configured model not being good enough at structured generation. Both
         * are the same 503 to the client, whose remedy is identical.
         */
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);
        llm.replying("Sure! Here is your analysis of the tenancy agreement.");

        assertThrows(AiAnswerUnavailableException.class, this::analyze);

        assertEquals(1d, counter("invalid_output"));
        assertEquals(0d, counter("llm_failure"));
        assertEquals(0d, counter("success"));
    }

    @Test
    @DisplayName("an INCOMPLETE reply is refused rather than back-filled")
    void incompleteReplyIsRefused() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);
        llm.replying(AnalysisFixtures.replyWithout(AnalysisJsonParser.FIELD_RISKS));

        assertThrows(AiAnswerUnavailableException.class, this::analyze);
        assertEquals(1d, counter("invalid_output"));
    }

    // ------------------------------------------------------------ bounding

    @Test
    @DisplayName("the prompt is BOUNDED and the response says so")
    void oversizedDocumentIsTruncatedAndReported() {
        loader.result = new AnalysisDocument(AnalysisFixtures.DOCUMENT_ID,
                AnalysisFixtures.DOCUMENT_NAME, AiDocumentStatus.READY,
                AnalysisFixtures.distinctChunks(10, 1200));

        DocumentAnalysis analysis = analyze();

        assertTrue(analysis.truncated(),
                "a document over the context budget must report truncated=true");

        String userPrompt = llm.lastRequest().userPrompt();
        assertFalse(userPrompt.contains("CHUNK-9-MARKER"),
                "the whole document was sent despite the budget");
        assertTrue(userPrompt.contains("CHUNK-0-MARKER"),
                "the leading chunks must be the ones kept");
    }

    @Test
    @DisplayName("a document that fits reports truncated=false")
    void documentThatFitsIsNotFlagged() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);

        assertFalse(analyze().truncated());
    }

    // -------------------------------------------------------------- safety

    @Test
    @DisplayName("NO REAL INFERENCE SERVER IS EVER CONTACTED")
    void noRealModelIsCalled() {
        /*
         * The guard that keeps this class independent of what happens to be
         * installed on the machine running it. The client is a fake by
         * construction, so a developer with Ollama listening on localhost:11434
         * gets exactly the same result as CI, which has none.
         */
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);

        analyze();

        assertEquals("scripted", llm.providerName());
        assertEquals(1, llm.callCount());
        assertEquals(AnalysisPromptBuilder.OPERATION, llm.lastRequest().operation());
    }

    @Test
    @DisplayName("DocumentAnalysis.toString() does not print the analysis")
    void analysisToStringIsSafe() {
        loader.result = AnalysisFixtures.ready(AnalysisFixtures.CLAUSE);

        String rendered = analyze().toString();

        assertFalse(rendered.contains("Ramesh"), "a party name leaked through toString()");
        assertFalse(rendered.contains("residential tenancy"),
                "the summary leaked through toString()");
        assertTrue(rendered.contains("<not shown>"));
    }
}
