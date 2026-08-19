package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.document.entity.AiDocument;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.embedding.Embedding;
import com.arshraj.vakilconnect.ai.embedding.EmbeddingClient;
import com.arshraj.vakilconnect.ai.embedding.EmbeddingException;
import com.arshraj.vakilconnect.common.exception.DocumentEmbeddingException;
import com.arshraj.vakilconnect.common.exception.DocumentExtractionException;
import com.arshraj.vakilconnect.common.exception.DocumentProcessingConflictException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The ingestion pipeline.
 *
 * ============================ THE THREE-STAGE DESIGN ========================
 *
 * The obvious implementation wraps everything in one @Transactional method.
 * That is wrong here, and not subtly: stage 2 makes N calls to a LOCAL
 * INFERENCE SERVER. A fifty-chunk document is fifty round trips at hundreds of
 * milliseconds each - tens of seconds, minutes for a large document. Holding a
 * database connection for that would tie up one pool slot per concurrent
 * upload, and on a free Neon tier with a small connection limit a handful of
 * simultaneous ingestions would starve every other request in the application.
 *
 * So the work is split by WHERE THE TIME GOES:
 *
 *   STAGE 1 (short transaction) - claim the document with a conditional UPDATE.
 *   STAGE 2 (NO TRANSACTION)    - extract, normalize, chunk, embed. The slow part.
 *   STAGE 3 (short transaction) - replace chunks and mark READY, atomically.
 *
 * The brief asked for an explanation if one transaction was impractical. It is
 * not too LARGE for one transaction - it is far too SLOW to belong in one.
 *
 * THIS CLASS CARRIES NO @Transactional ANYWHERE, and that is load-bearing
 * rather than an oversight. Every transaction lives on {@link
 * IngestionTransactions}, a separate bean, so the slow stage provably runs
 * outside one. Putting those methods here as private helpers would have been
 * worse than useless: Spring's @Transactional is proxy-based, so a
 * {@code this.claim(...)} call bypasses the proxy entirely and starts no
 * transaction at all - silently, with the annotations still sitting there
 * looking correct.
 *
 * WHAT THE SPLIT COSTS, STATED HONESTLY: between stages 1 and 3 the document is
 * PROCESSING with its PREVIOUS chunks still present and still retrievable. That
 * is the better trade - deleting first would leave the document unsearchable
 * for the whole run and unrecoverable if the process died mid-way. A crash
 * strands the row in PROCESSING; nothing sweeps that yet, recorded as a known
 * limitation on DocumentProcessingConflictException.
 *
 * NO DOCUMENT TEXT IS EVER LOGGED - not a chunk, not a preview, not on failure.
 */
@Service
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);

    /*
     * FIXED FAILURE REASONS, chosen from this list and nothing else.
     *
     * `ai_documents.failure_reason` is returned to the client and written to
     * logs, so it must never carry a parser message, an exception's
     * getMessage(), or anything derived from document content. Naming them as
     * constants makes that checkable rather than a discipline somebody has to
     * remember at each throw site.
     */
    static final String REASON_EXTRACTION = "Text could not be read from this document.";
    static final String REASON_NO_CHUNKS = "No indexable text was found in this document.";
    static final String REASON_EMBEDDING = "The embedding service was unavailable.";
    static final String REASON_UNEXPECTED = "Processing failed unexpectedly.";

    private final IngestionTransactions transactions;
    private final UserRepository userRepository;
    private final DocumentTextExtractor extractor;
    private final DocumentTextNormalizer normalizer;
    private final DocumentChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final AiIngestionMetrics metrics;

    public DocumentIngestionServiceImpl(IngestionTransactions transactions,
                                        UserRepository userRepository,
                                        DocumentTextExtractor extractor,
                                        DocumentTextNormalizer normalizer,
                                        DocumentChunker chunker,
                                        EmbeddingClient embeddingClient,
                                        AiIngestionMetrics metrics) {
        this.transactions = transactions;
        this.userRepository = userRepository;
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.metrics = metrics;
    }

    @Override
    public IngestionResult process(String userEmail, UUID documentId) {
        long startedAt = System.nanoTime();

        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        claimOrExplain(documentId, owner.getId());

        try {
            PreparedChunks prepared = prepare(documentId, owner.getId());

            transactions.replaceChunks(documentId, prepared, embeddingClient.dimension());

            metrics.recordSuccess();
            metrics.recordChunkCount(prepared.chunks().size());
            metrics.recordDuration(AiIngestionMetrics.STAGE_SUCCESS, elapsed(startedAt));

            // Counts only. Never a fragment of the text.
            log.info("Ingested document {}: {} chunks, model {}, {} dimensions",
                    documentId, prepared.chunks().size(), prepared.model(),
                    embeddingClient.dimension());

            return new IngestionResult(
                    documentId,
                    AiDocumentStatus.READY,
                    prepared.chunks().size(),
                    prepared.totalCharacters(),
                    prepared.model(),
                    embeddingClient.dimension());

        } catch (DocumentExtractionException e) {
            transactions.markFailed(documentId, REASON_EXTRACTION);
            metrics.recordExtractionFailure();
            metrics.recordDuration(AiIngestionMetrics.STAGE_EXTRACTION_FAILURE, elapsed(startedAt));
            throw e;

        } catch (EmbeddingException e) {
            transactions.markFailed(documentId, REASON_EMBEDDING);
            metrics.recordEmbeddingFailure();
            metrics.recordDuration(AiIngestionMetrics.STAGE_EMBEDDING_FAILURE, elapsed(startedAt));
            /*
             * Rethrown as a DOMAIN exception. The client is told the embedding
             * service is unavailable and gets a 503; the underlying cause is
             * attached for the stack trace, but its message - which may name a
             * model or a host - never becomes the response body.
             */
            throw new DocumentEmbeddingException("Embedding failed for document " + documentId, e);

        } catch (RuntimeException e) {
            /*
             * ANYTHING ELSE STILL LEAVES A DEFINITE STATE. A document must never
             * be stranded in PROCESSING by an exception nobody anticipated -
             * that is the one state with no recovery path.
             */
            transactions.markFailed(documentId, REASON_UNEXPECTED);
            metrics.recordFailure();
            metrics.recordDuration(AiIngestionMetrics.STAGE_FAILURE, elapsed(startedAt));
            log.error("Ingestion failed unexpectedly for document {}", documentId, e);
            throw e;
        }
    }

    // ------------------------------------------------------------- stage 1

    /**
     * Claims the document, or turns the failure into the right response.
     *
     * A failed claim means one of three things, and they must not collapse into
     * one answer. An owner-scoped read separates them: if the document is not
     * visible to this user the answer is 404 - identical to a document that
     * never existed, so probing ids reveals nothing about other users. If it IS
     * visible, the only remaining reason the conditional UPDATE matched nothing
     * is that it is already PROCESSING.
     */
    private void claimOrExplain(UUID documentId, UUID ownerId) {
        if (transactions.claim(documentId, ownerId)) {
            return;
        }
        if (!transactions.isVisibleToOwner(documentId, ownerId)) {
            throw new ResourceNotFoundException("Document not found");
        }
        throw new DocumentProcessingConflictException();
    }

    // ------------------------------------------------------------- stage 2

    /**
     * Extract, normalize, chunk, embed. Runs with NO transaction held.
     */
    private PreparedChunks prepare(UUID documentId, UUID ownerId) {
        AiDocument document = transactions.loadForIngestion(documentId, ownerId);

        String rawText = extractor.extract(document.getContent(), document.getContentType());
        String normalized = normalizer.normalize(rawText);

        List<TextChunk> chunks = chunker.chunk(normalized);
        if (chunks.isEmpty()) {
            /*
             * Extraction produced text but chunking produced nothing - a
             * document of only whitespace or parser artefacts. Treated as an
             * extraction failure because the outcome for the user is identical:
             * there is nothing here to index.
             */
            throw new DocumentExtractionException(REASON_NO_CHUNKS);
        }

        List<String> texts = chunks.stream().map(TextChunk::content).toList();

        /*
         * EVERY EMBEDDING BEFORE ANY WRITE.
         *
         * embedAll fails on the first error, so a partial failure produces no
         * vectors at all and stage 3 never runs. That is the "roll back if one
         * embedding fails" requirement satisfied by ORDERING rather than by a
         * transaction spanning minutes of inference - the database is never
         * asked to hold anything open while this happens.
         */
        List<Embedding> embeddings = embeddingClient.embedAll(texts);

        return new PreparedChunks(chunks, embeddings,
                embeddings.isEmpty() ? "none" : embeddings.get(0).model());
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
