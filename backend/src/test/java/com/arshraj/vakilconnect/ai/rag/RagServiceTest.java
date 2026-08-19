package com.arshraj.vakilconnect.ai.rag;

import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.PermanentLlmException;
import com.arshraj.vakilconnect.ai.embedding.AiEmbeddingProperties;
import com.arshraj.vakilconnect.ai.embedding.Embedding;
import com.arshraj.vakilconnect.ai.embedding.EmbeddingClient;
import com.arshraj.vakilconnect.ai.embedding.PermanentEmbeddingException;
import com.arshraj.vakilconnect.ai.embedding.StubEmbeddingClient;
import com.arshraj.vakilconnect.ai.AiMetrics;
import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
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
 * The RAG orchestration, with recording collaborators.
 *
 * A UNIT TEST, DELIBERATELY. The two properties that matter most here -
 * "the model was never called" and "the citations did not come from the model's
 * text" - are statements about CONTROL FLOW, and a recording fake proves them
 * exactly. An integration test can only show the outcome looked right, which is
 * consistent with the model having been called and its answer discarded.
 *
 * The real database path is covered by DocumentRetrievalIT, and the real HTTP
 * path by RagAskIT.
 */
@DisplayName("RagServiceImpl")
class RagServiceTest {

    private static final String EMAIL = "tenant@example.com";
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private static final String RAG_COUNTER = "vakilconnect.ai.rag.request";
    private static final String RAG_TIMER = "vakilconnect.ai.rag.duration";

    private final AiRetrievalProperties properties = new AiRetrievalProperties(6, 0.6, 8000, 4000);

    private MeterRegistry registry;
    private RecordingLlmClient llm;
    private RecordingRetriever retriever;
    private EmbeddingClient embedding;
    private CountingEmbeddingClient countingEmbedding;
    private RagServiceImpl service;

    /** Counts embed() calls so "embedded exactly once" is provable. */
    private static final class CountingEmbeddingClient implements EmbeddingClient {
        private final EmbeddingClient delegate;
        private final List<String> embedded = new ArrayList<>();
        private RuntimeException failure;

        CountingEmbeddingClient(EmbeddingClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Embedding embed(String text) {
            embedded.add(text);
            if (failure != null) {
                throw failure;
            }
            return delegate.embed(text);
        }

        @Override
        public List<Embedding> embedAll(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimension() {
            return delegate.dimension();
        }

        @Override
        public String providerName() {
            return delegate.providerName();
        }
    }

    /** Returns whatever the test stages, and records the owner it was asked for. */
    private static final class RecordingRetriever implements DocumentRetriever {
        private List<RetrievedChunk> result = List.of();
        private final List<UUID> owners = new ArrayList<>();

        @Override
        public List<RetrievedChunk> retrieve(UUID ownerId, Embedding queryEmbedding) {
            owners.add(ownerId);
            return result;
        }
    }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        AiMetrics aiMetrics = new AiMetrics(registry);

        embedding = new StubEmbeddingClient(
                new AiEmbeddingProperties(AiEmbeddingProperties.STUB, "nomic-embed-text", 768),
                aiMetrics);
        countingEmbedding = new CountingEmbeddingClient(embedding);

        llm = new RecordingLlmClient();
        retriever = new RecordingRetriever();

        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setEmail(EMAIL);

        /*
         * THE ONLY MOCK IN THIS SUITE, and it is confined to one line.
         *
         * Every other fake here is hand-rolled, matching the rest of the
         * project. UserRepository is the exception because it extends
         * JpaRepository: implementing it by hand means stubbing dozens of
         * inherited methods to express "look up one user", which would obscure
         * the test rather than clarify it. Mockito ships with
         * spring-boot-starter-test, so this adds no dependency.
         */
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(owner));

        service = new RagServiceImpl(users, countingEmbedding, retriever,
                new RagContextBuilder(properties), new RagPromptBuilder(),
                llm, properties, new RagMetrics(registry));
    }

    private double counter(String outcome) {
        var c = registry.find(RAG_COUNTER).tag("outcome", outcome).counter();
        return c == null ? 0d : c.count();
    }

    // ------------------------------------------------- THE NO-EVIDENCE RULE

    @Test
    @DisplayName("NO RELEVANT CHUNKS -> the model is NEVER called")
    void noEvidenceSkipsTheModelEntirely() {
        /*
         * THE SINGLE MOST IMPORTANT TEST IN THIS CLASS.
         *
         * A model handed an empty context and a legal question does not say "I
         * don't know" - it produces a fluent, plausible, entirely invented
         * answer, because that is what the training objective rewards. Not
         * calling it at all is the strongest hallucination control the feature
         * has, and it also saves tens of seconds of local CPU inference for a
         * question that had no answer available.
         *
         * Asserted on the CALL COUNT, which is the only way to prove a negative
         * about control flow.
         */
        retriever.result = List.of();

        RagAnswer answer = service.ask(EMAIL, "What is the notice period?");

        assertEquals(0, llm.callCount(), "the model must not be called with no evidence");
        assertFalse(answer.grounded());
        assertTrue(answer.sources().isEmpty(), "no evidence means no citations");
        assertEquals(RagPromptBuilder.INSUFFICIENT_PHRASE, answer.answer());
        assertEquals(1d, counter("insufficient_evidence"));
    }

    @Test
    @DisplayName("the question is still embedded once, so retrieval can be attempted")
    void embedsExactlyOncePerQuestion() {
        retriever.result = List.of(RagFixtures.chunk(0, "Thirty days notice.", 0.1));

        service.ask(EMAIL, "notice period?");

        assertEquals(1, countingEmbedding.embedded.size(),
                "embedding twice would double local inference cost for no benefit");
        assertEquals("notice period?", countingEmbedding.embedded.get(0));
    }

    // ------------------------------------------------------- orchestration

    @Test
    @DisplayName("retrieval is scoped to the AUTHENTICATED owner, resolved from the email")
    void retrievalUsesTheResolvedOwner() {
        retriever.result = List.of(RagFixtures.chunk(0, "evidence", 0.1));

        service.ask(EMAIL, "question?");

        assertEquals(List.of(OWNER_ID), retriever.owners,
                "the owner must come from the resolved account, never from the request");
    }

    @Test
    @DisplayName("evidence and question both reach the prompt, correctly separated")
    void evidenceAndQuestionReachThePrompt() {
        String evidence = "Clause 3. Either party may terminate on 30 days written notice.";
        retriever.result = List.of(RagFixtures.chunk(4, evidence, 0.12));

        service.ask(EMAIL, "What is the notice period?");

        assertEquals(1, llm.callCount());
        var request = llm.lastRequest();

        assertTrue(request.systemPrompt().contains("Never invent a law"));
        assertTrue(request.userPrompt().contains(evidence));
        assertTrue(request.userPrompt().contains("What is the notice period?"));
        assertTrue(request.userPrompt().indexOf("What is the notice period?")
                        > request.userPrompt().indexOf(RagPromptBuilder.CONTEXT_CLOSE),
                "the question must sit outside the untrusted fence");
        assertEquals(RagPromptBuilder.OPERATION, request.operation());
    }

    // ------------------------------------------------- CITATION PROVENANCE

    @Test
    @DisplayName("SOURCES COME FROM RETRIEVAL, and a lying model cannot add one")
    void modelCannotInventASource() {
        /*
         * THE CITATION GUARANTEE.
         *
         * The model here returns prose naming a source number that does not
         * exist and a document nobody uploaded. If citations were parsed out of
         * the answer - or if the model were asked which sources it used - both
         * would appear in the response. They must not: the source list is mapped
         * from the retrieval results, and the answer text is never read.
         */
        retriever.result = List.of(
                RagFixtures.chunk(RagFixtures.DOCUMENT_A, RagFixtures.NAME_A, 4, "real", 0.1));

        llm.answering("Per [Source 999] in secret-document.pdf, the answer is 42. "
                + "See also confidential-merger.pdf chunk 77.");

        RagAnswer answer = service.ask(EMAIL, "question?");

        assertEquals(1, answer.sources().size(), "exactly the retrieved chunk, nothing more");

        RagSource source = answer.sources().get(0);
        assertEquals(RagFixtures.DOCUMENT_A, source.documentId());
        assertEquals(RagFixtures.NAME_A, source.documentName());
        assertEquals(4, source.chunkIndex());

        // The fabricated references survive in the PROSE - that is the model's
        // output and is returned as-is - but never become structured citations.
        assertTrue(answer.answer().contains("secret-document.pdf"));
        assertFalse(answer.sources().stream()
                        .anyMatch(s -> s.documentName().contains("secret-document")),
                "a document the model invented must never appear as a source");
        assertFalse(answer.sources().stream().anyMatch(s -> s.chunkIndex() == 999));
    }

    @Test
    @DisplayName("several retrieved chunks produce several sources, in retrieval order")
    void multipleSourcesArePreserved() {
        retriever.result = List.of(
                RagFixtures.chunk(RagFixtures.DOCUMENT_A, RagFixtures.NAME_A, 1, "first", 0.10),
                RagFixtures.chunk(RagFixtures.DOCUMENT_B, RagFixtures.NAME_B, 7, "second", 0.20));

        RagAnswer answer = service.ask(EMAIL, "question?");

        assertEquals(2, answer.sources().size());
        assertEquals(RagFixtures.NAME_A, answer.sources().get(0).documentName());
        assertEquals(1, answer.sources().get(0).chunkIndex());
        assertEquals(RagFixtures.NAME_B, answer.sources().get(1).documentName());
        assertEquals(7, answer.sources().get(1).chunkIndex());
    }

    @Test
    @DisplayName("a source carries a truncated excerpt, not the whole chunk")
    void sourceExcerptIsBounded() {
        // A full chunk per citation would make the response mostly document
        // text, which is what the document endpoint is for.
        retriever.result = List.of(RagFixtures.chunk(0, "x".repeat(2000), 0.1));

        String excerpt = service.ask(EMAIL, "question?").sources().get(0).excerpt();

        assertTrue(excerpt.length() < 300, "excerpt was " + excerpt.length() + " characters");
        assertTrue(excerpt.endsWith("…"));
    }

    // ------------------------------------------------------ context limits

    @Test
    @DisplayName("context is bounded, and sources stay aligned with what the model saw")
    void contextIsBoundedAndSourcesStayAligned() {
        /*
         * Twelve chunks of ~1200 characters is far past the 8000-character
         * budget. The critical assertion is not merely that the prompt is
         * smaller - it is that the SOURCE LIST matches exactly the chunks that
         * made it into the prompt. Citing evidence the model never saw would be
         * a fabricated citation produced by our own code rather than the
         * model's.
         */
        retriever.result = RagFixtures.chunks(12, 1200);

        RagAnswer answer = service.ask(EMAIL, "question?");

        String userPrompt = llm.lastRequest().userPrompt();
        assertTrue(userPrompt.length() < 9000,
                "context was not bounded: " + userPrompt.length() + " characters");
        assertTrue(answer.truncated(), "dropping evidence must be reported, not silent");

        assertTrue(answer.sources().size() < 12, "some chunks must have been dropped");
        for (RagSource source : answer.sources()) {
            assertTrue(userPrompt.contains("Chunk: " + source.chunkIndex()),
                    "source chunk " + source.chunkIndex() + " was cited but never shown "
                            + "to the model");
        }
    }

    // ------------------------------------------------------ failure paths

    @Test
    @DisplayName("a transient model failure becomes a controlled AI error")
    void transientLlmFailureIsControlled() {
        retriever.result = List.of(RagFixtures.chunk(0, "evidence", 0.1));
        llm.failingWith(new LlmException("Ollama unreachable at http://localhost:11434"));

        AiAnswerUnavailableException thrown = assertThrows(AiAnswerUnavailableException.class,
                () -> service.ask(EMAIL, "question?"));

        // The provider's own message may name a host or a model. It stays in the
        // cause for the stack trace and never becomes the domain message.
        assertFalse(thrown.getMessage().contains("localhost:11434"));
        assertEquals(1d, counter("llm_failure"));
    }

    @Test
    @DisplayName("a permanent model failure is controlled the same way")
    void permanentLlmFailureIsControlled() {
        retriever.result = List.of(RagFixtures.chunk(0, "evidence", 0.1));
        llm.failingWith(new PermanentLlmException("model 'llama3.2' not found"));

        assertThrows(AiAnswerUnavailableException.class, () -> service.ask(EMAIL, "question?"));
        assertEquals(1d, counter("llm_failure"));
    }

    @Test
    @DisplayName("a model that produces nothing is a failure, not an empty grounded answer")
    void emptyModelOutputIsAFailure() {
        /*
         * The worst possible output would be an empty answer WITH citations
         * attached: it looks grounded, names real documents, and says nothing.
         *
         * Note the realistic shape of this failure. LlmResponse refuses blank
         * text by construction, so a provider returning nothing surfaces as an
         * exception from the client rather than as an empty response object -
         * which is exactly what the recording client reproduces. The service's
         * own blank check is defence-in-depth against a future client that does
         * not uphold that invariant.
         */
        retriever.result = List.of(RagFixtures.chunk(0, "evidence", 0.1));
        llm.answering("");

        assertThrows(AiAnswerUnavailableException.class, () -> service.ask(EMAIL, "question?"));
    }

    @Test
    @DisplayName("an embedding failure becomes a controlled AI error, and never reaches the model")
    void embeddingFailureIsControlled() {
        countingEmbedding.failure = new PermanentEmbeddingException("model not pulled");

        assertThrows(AiAnswerUnavailableException.class, () -> service.ask(EMAIL, "question?"));

        assertEquals(0, llm.callCount(), "no embedding means no retrieval and no model call");
        assertEquals(1d, counter("failure"));
    }

    // ------------------------------------------------- question validation

    @Test
    @DisplayName("a blank question is rejected BEFORE embedding or retrieval")
    void blankQuestionIsRejectedEarly() {
        for (String blank : new String[]{ "", "   ", "\n\t ", null }) {
            assertThrows(IllegalArgumentException.class, () -> service.ask(EMAIL, blank));
        }
        assertEquals(0, countingEmbedding.embedded.size(), "nothing should have been embedded");
        assertEquals(0, llm.callCount());
        assertTrue(retriever.owners.isEmpty(), "retrieval must not have run");
    }

    @Test
    @DisplayName("an over-long question is rejected before any inference")
    void oversizedQuestionIsRejectedEarly() {
        // A bound, not a courtesy: an unbounded question is both an inference
        // cost and a way to push the system rules out of a small model's window.
        String tooLong = "a".repeat(properties.maxQuestionCharacters() + 1);

        assertThrows(IllegalArgumentException.class, () -> service.ask(EMAIL, tooLong));
        assertEquals(0, countingEmbedding.embedded.size());
        assertEquals(0, llm.callCount());
    }

    @Test
    @DisplayName("a question at exactly the limit is accepted")
    void questionAtTheLimitIsAccepted() {
        retriever.result = List.of(RagFixtures.chunk(0, "evidence", 0.1));

        service.ask(EMAIL, "a".repeat(properties.maxQuestionCharacters()));

        assertEquals(1, llm.callCount());
    }

    // -------------------------------------------------------------- metrics

    @Test
    @DisplayName("a grounded answer records success, chunk count and latency")
    void metricsOnSuccess() {
        retriever.result = List.of(RagFixtures.chunk(0, "evidence", 0.1));

        service.ask(EMAIL, "question?");

        assertEquals(1d, counter("success"));
        assertEquals(1L, registry.find(RAG_TIMER).tag("outcome", "success").timer().count());
        assertEquals(1L, registry.find("vakilconnect.ai.rag.retrieved.chunks").summary().count());
    }

    @Test
    @DisplayName("metric tags carry ONLY the bounded outcome, never user data")
    void metricTagsAreBounded() {
        /*
         * Tag values become time series, so an unbounded one degrades the whole
         * registry and a personal one copies user data into a store with a
         * completely different access-control model from the database.
         */
        retriever.result = List.of(
                RagFixtures.chunk(RagFixtures.DOCUMENT_A, "very-secret-contract.pdf",
                        0, "confidential settlement terms", 0.1));

        service.ask(EMAIL, "what is the settlement amount?");

        registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("vakilconnect.ai.rag"))
                .forEach(meter -> meter.getId().getTags().forEach(tag -> {
                    assertEquals("outcome", tag.getKey(),
                            "the only permitted tag key is `outcome`, found " + tag.getKey());
                    assertTrue(List.of("success", "insufficient_evidence",
                                    "llm_failure", "failure").contains(tag.getValue()),
                            "unbounded tag value: " + tag.getValue());
                }));

        // And nothing user-derived leaked into a meter NAME either.
        registry.getMeters().forEach(meter -> {
            String name = meter.getId().getName();
            assertFalse(name.contains(EMAIL));
            assertFalse(name.contains("secret"));
        });
    }
}
