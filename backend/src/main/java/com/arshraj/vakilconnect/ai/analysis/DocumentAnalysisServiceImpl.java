package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.LlmResponse;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException;
import com.arshraj.vakilconnect.common.exception.DocumentNotAnalyzableException;
import com.arshraj.vakilconnect.common.exception.DocumentProcessingConflictException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * The analysis pipeline.
 *
 * owner -> owner-scoped load -> state check -> bounded context -> structured
 * prompt -> local model -> strict parse -> identity from the database
 *
 * ================= THE PROPERTY THAT MATTERS MOST HERE =====================
 *
 * THE MODEL CANNOT CHANGE WHICH DOCUMENT THIS IS. `documentId` and
 * `documentName` on the response are taken from {@link AnalysisDocument} - the
 * row an owner-scoped query returned before the model was called - and the
 * parser produces an {@link AnalysisContent}, which has no components for them.
 * A document instructing the model to emit a different id therefore has nothing
 * to instruct: the field it would need to reach does not exist on the type the
 * model's output becomes.
 *
 * The corollary is the one worth stating out loud: THE MODEL MAKES NO
 * AUTHORIZATION DECISION AND IS NOT ASKED TO. Ownership was settled in SQL, two
 * steps before generation, and nothing downstream re-opens it.
 *
 * NOT @Transactional, deliberately, and for AI-3's reason. The loader opens its
 * own short read transaction; the generation that follows can take tens of
 * seconds against a local CPU model, and holding a connection across it would
 * tie up one pool slot per concurrent analysis on a free Neon tier with very few
 * of them. There is no @Transactional anywhere on this class, so it cannot
 * accidentally acquire one.
 *
 * NEVER LOGS THE DOCUMENT, THE PROMPT OR THE MODEL'S REPLY. Counts, ids and
 * fixed strings only.
 */
@Service
public class DocumentAnalysisServiceImpl implements DocumentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisServiceImpl.class);

    private final UserRepository userRepository;
    private final AnalysisDocumentLoader loader;
    private final AnalysisContextBuilder contextBuilder;
    private final AnalysisPromptBuilder promptBuilder;
    private final AnalysisJsonParser parser;
    private final LlmClient llmClient;
    private final AnalysisMetrics metrics;

    public DocumentAnalysisServiceImpl(UserRepository userRepository,
                                       AnalysisDocumentLoader loader,
                                       AnalysisContextBuilder contextBuilder,
                                       AnalysisPromptBuilder promptBuilder,
                                       AnalysisJsonParser parser,
                                       LlmClient llmClient,
                                       AnalysisMetrics metrics) {
        this.userRepository = userRepository;
        this.loader = loader;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.llmClient = llmClient;
        this.metrics = metrics;
    }

    @Override
    public DocumentAnalysis analyze(String userEmail, UUID documentId) {
        long startedAt = System.nanoTime();

        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        /*
         * OWNERSHIP FIRST, AND IN THE QUERY. An empty Optional means the id does
         * not exist OR is not this user's, and the two are indistinguishable
         * here on purpose: a 403 for somebody else's document would confirm that
         * the document exists, turning this endpoint into an oracle for what
         * other users have uploaded. Same anti-enumeration convention as every
         * other route on /api/ai/documents.
         */
        AnalysisDocument document = loader.load(documentId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        requireAnalyzable(document);

        AnalysisContext context = contextBuilder.build(document.chunks());
        if (context.isEmpty()) {
            /*
             * Every chunk was individually larger than the whole context budget.
             * Pathological - AI-2 caps a chunk at 1200 characters and the budget
             * floor is 500 - but analysing nothing and calling it an analysis
             * would be worse than refusing.
             */
            throw new DocumentNotAnalyzableException(DocumentNotAnalyzableException.NO_TEXT);
        }

        AnalysisContent content = generate(context, startedAt);

        metrics.recordSuccess();
        metrics.recordDuration(AnalysisMetrics.OUTCOME_SUCCESS, elapsed(startedAt));

        // Counts only. Never a fragment of the document or of the analysis.
        log.info("Analysed document {}: {} chunk(s), truncated={}",
                document.documentId(), context.chunkCount(), context.truncated());

        /*
         * IDENTITY FROM THE DATABASE ROW, CONTENT FROM THE MODEL. The two
         * sources meet on exactly this line and nowhere else.
         */
        return DocumentAnalysis.of(document.documentId(), document.documentName(),
                content, context.truncated());
    }

    /**
     * Refuses a document that has not been through AI-2's pipeline.
     *
     * THREE STATES, TWO ANSWERS, and the split is by what the client should do.
     * PROCESSING means wait and poll, which is exactly what
     * DocumentProcessingConflictException already says. PENDING and FAILED both
     * mean "run processing first" - FAILED is folded in with PENDING rather than
     * given its own code because the remedy is the same and the reason for the
     * earlier failure is already readable on {@code GET /api/ai/documents/{id}}.
     */
    private static void requireAnalyzable(AnalysisDocument document) {
        if (document.status() == AiDocumentStatus.PROCESSING) {
            throw new DocumentProcessingConflictException();
        }
        if (!document.isReady()) {
            throw new DocumentNotAnalyzableException(DocumentNotAnalyzableException.NOT_PROCESSED);
        }
        if (!document.hasText()) {
            throw new DocumentNotAnalyzableException(DocumentNotAnalyzableException.NO_TEXT);
        }
    }

    /**
     * Calls the model and insists the reply is usable structured output.
     *
     * TWO DISTINCT FAILURES, COUNTED SEPARATELY. `llm_failure` is the model
     * being unreachable or silent - an infrastructure problem. `invalid_output`
     * is a model that answered fluently and did not follow the schema - a MODEL
     * QUALITY problem, and the one that decides whether a given local model is
     * good enough for this feature. Both surface to the client as the same 503,
     * because the client's remedy is the same; only the operator needs them
     * apart.
     */
    private AnalysisContent generate(AnalysisContext context, long startedAt) {
        String reply;
        try {
            LlmResponse response = llmClient.complete(LlmRequest.of(
                    AnalysisPromptBuilder.OPERATION,
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(context)));

            reply = response.text();

        } catch (LlmException e) {
            metrics.recordLlmFailure();
            metrics.recordDuration(AnalysisMetrics.OUTCOME_LLM_FAILURE, elapsed(startedAt));
            /*
             * Rethrown as a DOMAIN exception. The cause is attached for the
             * stack trace, but its message - which may name a model or a host -
             * never becomes the response body; the handler substitutes a fixed
             * sentence.
             */
            throw new AiAnswerUnavailableException("The model could not be reached", e);
        }

        try {
            return parser.parse(reply);

        } catch (AiAnswerUnavailableException e) {
            metrics.recordInvalidOutput();
            metrics.recordDuration(AnalysisMetrics.OUTCOME_INVALID_OUTPUT, elapsed(startedAt));
            throw e;
        }
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
