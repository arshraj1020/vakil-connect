package com.arshraj.vakilconnect.ai.rag;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.LlmResponse;
import com.arshraj.vakilconnect.ai.embedding.Embedding;
import com.arshraj.vakilconnect.ai.embedding.EmbeddingClient;
import com.arshraj.vakilconnect.ai.embedding.EmbeddingException;
import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * The RAG pipeline.
 *
 * question -> embed -> user-scoped vector search -> threshold -> context ->
 * grounded prompt -> local model -> validate -> attach citations
 *
 * ================== THE TWO PROPERTIES THAT MATTER MOST ====================
 *
 * 1. CITATIONS COME FROM RETRIEVAL, NEVER FROM THE MODEL.
 *
 *    The obvious implementation asks the model which sources it used, or parses
 *    "[Source 2]" out of its prose. Both are wrong, and not subtly: a model can
 *    be talked into claiming any source, including one it never saw, and a
 *    document containing the text "[Source 9]" would inject a citation directly.
 *    So the source list is mapped from the retrieval results - rows the database
 *    returned under an ownership predicate - and the model's output is treated
 *    purely as prose. It cannot add, remove or rename a citation.
 *
 * 2. NO EVIDENCE MEANS NO MODEL CALL.
 *
 *    When retrieval returns nothing within the distance threshold, this returns
 *    an insufficient-evidence answer WITHOUT invoking the model. That is the
 *    single most effective hallucination control in the whole feature: a model
 *    handed an empty context and a legal question will usually produce a fluent,
 *    plausible, entirely invented answer. It also saves tens of seconds of local
 *    CPU inference for a question that had no answer available.
 *
 * NOT @Transactional. The retriever opens its own short read transaction; the
 * model call that follows can take tens of seconds against a local CPU model,
 * and holding a connection across it would tie up a pool slot per concurrent
 * question - the same reasoning that shaped AI-2's ingestion stages.
 *
 * NEVER LOGS THE QUESTION, THE CONTEXT OR THE ANSWER.
 */
@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final UserRepository userRepository;
    private final EmbeddingClient embeddingClient;
    private final DocumentRetriever retriever;
    private final RagContextBuilder contextBuilder;
    private final RagPromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final AiRetrievalProperties properties;
    private final RagMetrics metrics;

    public RagServiceImpl(UserRepository userRepository,
                          EmbeddingClient embeddingClient,
                          DocumentRetriever retriever,
                          RagContextBuilder contextBuilder,
                          RagPromptBuilder promptBuilder,
                          LlmClient llmClient,
                          AiRetrievalProperties properties,
                          RagMetrics metrics) {
        this.userRepository = userRepository;
        this.embeddingClient = embeddingClient;
        this.retriever = retriever;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public RagAnswer ask(String userEmail, String question) {
        long startedAt = System.nanoTime();

        String trimmed = question == null ? "" : question.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Ask a question about your documents.");
        }
        if (trimmed.length() > properties.maxQuestionCharacters()) {
            /*
             * Enforced HERE as well as by @Size on the request record. The
             * annotation is the fast path for HTTP callers; this is the
             * authority, because the service is reachable from anywhere and the
             * limit is a control on prompt size rather than a formatting rule.
             */
            throw new IllegalArgumentException("That question is too long.");
        }

        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // ---------------------------------------------------- embed + retrieve
        List<RetrievedChunk> retrieved;
        try {
            Embedding queryEmbedding = embeddingClient.embed(trimmed);
            retrieved = retriever.retrieve(owner.getId(), queryEmbedding);

        } catch (EmbeddingException e) {
            metrics.recordFailure();
            metrics.recordDuration(RagMetrics.OUTCOME_FAILURE, elapsed(startedAt));
            throw new AiAnswerUnavailableException(
                    "Could not embed the question", e);
        }

        // ------------------------------------------------- no evidence, no call
        if (retrieved.isEmpty()) {
            metrics.recordRetrievalMiss();
            metrics.recordDuration(RagMetrics.OUTCOME_INSUFFICIENT, elapsed(startedAt));

            log.debug("No chunk within distance {} - answering insufficient-evidence "
                    + "without calling the model", properties.maxDistance());

            return RagAnswer.insufficientEvidence();
        }

        metrics.recordRetrievalHit(retrieved.size());

        RagContext context = contextBuilder.build(retrieved);
        if (context.isEmpty()) {
            // Every retrieved chunk was individually larger than the whole
            // context budget. Pathological, but answering from nothing would be
            // worse than saying so.
            metrics.recordRetrievalMiss();
            metrics.recordDuration(RagMetrics.OUTCOME_INSUFFICIENT, elapsed(startedAt));
            return RagAnswer.insufficientEvidence();
        }

        // ------------------------------------------------------------- answer
        String answer = complete(context, trimmed, startedAt);

        /*
         * SOURCES FROM THE CONTEXT, NOT FROM THE ANSWER.
         *
         * context.chunks() is what the model was actually shown - a subset of
         * retrieval after truncation - so every citation returned corresponds to
         * evidence that existed and was supplied. Nothing here reads `answer`.
         */
        List<RagSource> sources = context.chunks().stream().map(RagSource::from).toList();

        metrics.recordSuccess();
        metrics.recordDuration(RagMetrics.OUTCOME_SUCCESS, elapsed(startedAt));

        // Counts only. Never the question, the context or the answer.
        log.info("Answered from {} chunk(s), {} source(s), truncated={}",
                context.chunks().size(), sources.size(), context.truncated());

        return RagAnswer.grounded(answer, sources, context.truncated());
    }

    /**
     * Calls the model and insists the output is usable.
     *
     * A blank completion is a FAILURE, not an empty answer. Returning "" with a
     * list of citations attached would be the worst possible output: it looks
     * grounded, cites real documents, and says nothing.
     */
    private String complete(RagContext context, String question, long startedAt) {
        try {
            LlmResponse response = llmClient.complete(LlmRequest.of(
                    RagPromptBuilder.OPERATION,
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(question, context)));

            String answer = response.text() == null ? "" : response.text().strip();
            if (answer.isEmpty()) {
                throw new AiAnswerUnavailableException(
                        "The model returned an empty answer", null);
            }
            return answer;

        } catch (AiAnswerUnavailableException e) {
            metrics.recordLlmFailure();
            metrics.recordDuration(RagMetrics.OUTCOME_FAILURE, elapsed(startedAt));
            throw e;

        } catch (LlmException e) {
            metrics.recordLlmFailure();
            metrics.recordDuration(RagMetrics.OUTCOME_FAILURE, elapsed(startedAt));
            /*
             * Rethrown as a DOMAIN exception. The client learns the assistant is
             * unavailable and gets a 503; the cause is attached for the stack
             * trace but its message - which may name a model or a host - never
             * becomes the response body.
             */
            throw new AiAnswerUnavailableException("The model could not be reached", e);
        }
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
