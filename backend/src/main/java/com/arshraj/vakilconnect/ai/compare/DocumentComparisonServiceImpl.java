package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.ai.LlmClient;
import com.arshraj.vakilconnect.ai.LlmException;
import com.arshraj.vakilconnect.ai.LlmRequest;
import com.arshraj.vakilconnect.ai.LlmResponse;
import com.arshraj.vakilconnect.ai.analysis.AnalysisContext;
import com.arshraj.vakilconnect.ai.analysis.AnalysisDocument;
import com.arshraj.vakilconnect.ai.analysis.AnalysisDocumentLoader;
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
 * The comparison pipeline.
 *
 * owner -> two independent owner-scoped loads -> state checks on both ->
 * two bounded contexts -> two-document prompt -> local model -> strict parse
 * -> identity of BOTH documents from the database
 *
 * ============ NO SECOND EXTRACTION PATH, NO NEW VECTOR TABLE =================
 *
 * This class reads AI-2's chunks through {@link AnalysisDocumentLoader} - the
 * SAME loader AI-4 uses - and calls the SAME {@link LlmClient} every other AI
 * feature calls. Nothing here parses a file, computes an embedding, or issues
 * a vector search: a comparison is a question about two ALREADY-INDEXED
 * documents, so it needs their text and nothing upstream of it.
 *
 * =================== THE PROPERTY THAT MATTERS MOST HERE =====================
 *
 * NEITHER DOCUMENT'S IDENTITY CAN BE FORGED OR SWAPPED BY THE MODEL.
 * {@code firstDocumentId}/{@code firstDocumentName} and their "second"
 * counterparts come from the two {@link AnalysisDocument} values this class
 * loaded before the model was ever called; {@link ComparisonJsonParser}
 * produces a {@link ComparisonContent}, which has no field either document's
 * identity could occupy. A document instructing the model to "call yourself
 * document B" or to emit a different document id therefore has nothing to
 * instruct - the type the model's output becomes simply has no such field.
 *
 * BOTH DOCUMENTS ARE LOADED BEFORE EITHER IS COMPARED. If either lookup fails
 * - not found, not owned, wrong state - the failure is reported and the model
 * is NEVER CALLED. A caller cannot probe for the existence of one document by
 * pairing it with one they know they own and reading the response's shape,
 * because both failure paths return before any comparison work begins.
 *
 * NOT @Transactional, for AI-3 and AI-4's reason: the loader opens its own
 * short read transaction per call, and the model call that follows can take
 * tens of seconds against a local CPU model - doubly so here, since two
 * documents' worth of context reach the prompt.
 */
@Service
public class DocumentComparisonServiceImpl implements DocumentComparisonService {

    private static final Logger log = LoggerFactory.getLogger(DocumentComparisonServiceImpl.class);

    private final UserRepository userRepository;
    private final AnalysisDocumentLoader loader;
    private final ComparisonContextBuilder contextBuilder;
    private final ComparisonPromptBuilder promptBuilder;
    private final ComparisonJsonParser parser;
    private final LlmClient llmClient;
    private final ComparisonMetrics metrics;

    public DocumentComparisonServiceImpl(UserRepository userRepository,
                                         AnalysisDocumentLoader loader,
                                         ComparisonContextBuilder contextBuilder,
                                         ComparisonPromptBuilder promptBuilder,
                                         ComparisonJsonParser parser,
                                         LlmClient llmClient,
                                         ComparisonMetrics metrics) {
        this.userRepository = userRepository;
        this.loader = loader;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.llmClient = llmClient;
        this.metrics = metrics;
    }

    @Override
    public DocumentComparison compare(String userEmail, UUID documentId, UUID compareToDocumentId) {
        long startedAt = System.nanoTime();

        if (documentId != null && documentId.equals(compareToDocumentId)) {
            /*
             * Checked BEFORE any lookup, so it costs nothing and reveals
             * nothing about whether that id even exists. A document cannot
             * meaningfully be compared with itself, and letting the request
             * through would spend a model call to say so in prose instead of
             * refusing it as the malformed request it is.
             */
            throw new IllegalArgumentException(
                    "Choose two different documents to compare.");
        }

        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        /*
         * BOTH LOOKUPS RUN BEFORE EITHER FAILURE IS RAISED. Capturing both
         * Optionals first, rather than chaining `.orElseThrow()` on the first
         * call, matters here specifically: chaining would let a request that
         * pairs a document the caller owns with one they do not short-circuit
         * on the FIRST lookup, so the query cost - and, on a sufficiently
         * loud timing channel, the response latency - would depend on which
         * position the unowned id was placed in. Running both first means the
         * work done is identical regardless of which id (if either) turns out
         * not to belong to the caller.
         */
        var firstLookup = loader.load(documentId, owner.getId());
        var secondLookup = loader.load(compareToDocumentId, owner.getId());

        AnalysisDocument first = firstLookup.orElseThrow(
                () -> new ResourceNotFoundException("Document not found"));
        AnalysisDocument second = secondLookup.orElseThrow(
                () -> new ResourceNotFoundException("Document not found"));

        requireAnalyzable(first);
        requireAnalyzable(second);

        AnalysisContext firstContext = contextBuilder.build(first.chunks());
        AnalysisContext secondContext = contextBuilder.build(second.chunks());

        if (firstContext.isEmpty() || secondContext.isEmpty()) {
            throw new DocumentNotAnalyzableException(DocumentNotAnalyzableException.NO_TEXT);
        }

        ComparisonContent content = generate(firstContext, secondContext, startedAt);

        boolean truncated = firstContext.truncated() || secondContext.truncated();

        metrics.recordSuccess();
        metrics.recordDuration(ComparisonMetrics.OUTCOME_SUCCESS, elapsed(startedAt));

        // Counts and ids only. Never a fragment of either document.
        log.info("Compared documents {} and {}: {}/{} chunk(s), truncated={}",
                first.documentId(), second.documentId(),
                firstContext.chunkCount(), secondContext.chunkCount(), truncated);

        return DocumentComparison.of(first, second, content, truncated);
    }

    /** Identical gate to AI-4's, applied to each document independently. */
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

    private ComparisonContent generate(AnalysisContext first, AnalysisContext second, long startedAt) {
        String reply;
        try {
            LlmResponse response = llmClient.complete(LlmRequest.of(
                    ComparisonPromptBuilder.OPERATION,
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(first, second)));

            reply = response.text();

        } catch (LlmException e) {
            metrics.recordLlmFailure();
            metrics.recordDuration(ComparisonMetrics.OUTCOME_LLM_FAILURE, elapsed(startedAt));
            throw new AiAnswerUnavailableException("The model could not be reached", e);
        }

        try {
            return parser.parse(reply);

        } catch (AiAnswerUnavailableException e) {
            metrics.recordInvalidOutput();
            metrics.recordDuration(ComparisonMetrics.OUTCOME_INVALID_OUTPUT, elapsed(startedAt));
            throw e;
        }
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
