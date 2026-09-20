package com.arshraj.vakilconnect.ai.analysis;

import com.arshraj.vakilconnect.ai.document.dto.DocumentResponse;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentChunk;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentChunkRepository;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only database read the analysis feature performs.
 *
 * ================== OWNERSHIP IS ENFORCED BEFORE CONTENT LOADS ==============
 *
 * Both queries carry the owner id in their WHERE clause, and the metadata query
 * runs FIRST. A document belonging to somebody else produces an empty Optional
 * at step one and the chunk query is never issued - so another user's text is
 * not merely withheld from the response, it is never read out of the database at
 * all. That is the difference between "load, then check" and "check in the
 * query", and it is the reason this feature has no code path that holds another
 * user's content in memory even briefly.
 *
 * The status check is part of the same economy: a document that is not READY has
 * no chunks worth analysing, so they are not fetched.
 *
 * NO SECOND EXTRACTION PATH. This reads the chunks AI-2 already produced. Tika
 * is not invoked, the stored bytes are never loaded, and nothing is embedded -
 * there is exactly one place in this application that turns a file into text,
 * and it is DocumentIngestionServiceImpl.
 *
 * NO VECTOR SEARCH, EITHER. Analysis is about one known document, so there is no
 * query to embed and nothing to rank against; running a similarity search over a
 * synthetic query would be a made-up ordering dressed as a principled one. The
 * chunks come back in chunk_index order - the document's own - which is what
 * makes context selection deterministic.
 *
 * ==================== WHY THIS IS A BEAN AND NOT A METHOD ===================
 *
 * Spring's @Transactional is proxy-based, so a {@code this.load(...)} call from
 * DocumentAnalysisServiceImpl would bypass the proxy and start no transaction -
 * silently, with the annotation still sitting there looking correct. AI-2 hit
 * exactly that trap and answered it with IngestionTransactions; AI-3 answered it
 * by putting @Transactional on PgVectorDocumentRetriever rather than on
 * RagServiceImpl. This follows AI-3, being the same shape: one short read on its
 * own bean, so the service stays transaction-free across the slow model call.
 */
@Component
public class JpaAnalysisDocumentLoader implements AnalysisDocumentLoader {

    private final AiDocumentRepository documentRepository;
    private final AiDocumentChunkRepository chunkRepository;

    public JpaAnalysisDocumentLoader(AiDocumentRepository documentRepository,
                                     AiDocumentChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisDocument> load(UUID documentId, UUID ownerId) {
        if (documentId == null || ownerId == null) {
            /*
             * Defensive, and deliberately an exception rather than an empty
             * Optional. A null owner reaching here would mean the caller failed
             * to resolve the authenticated user, and answering "not found" would
             * turn a wiring defect into a plausible-looking 404 that nobody
             * would ever investigate. Same rule as PgVectorDocumentRetriever.
             */
            throw new IllegalStateException(
                    "analysis requires both a document id and an owner id");
        }

        Optional<DocumentResponse> metadata =
                documentRepository.findMetadataByIdAndOwner(documentId, ownerId);

        if (metadata.isEmpty()) {
            return Optional.empty();
        }

        DocumentResponse document = metadata.get();

        // Text is read only for a document that can actually be analysed.
        List<String> chunks = document.status() == AiDocumentStatus.READY
                ? chunkRepository.findByDocumentAndOwner(documentId, ownerId).stream()
                        .map(AiDocumentChunk::getContent)
                        .toList()
                : List.of();

        return Optional.of(new AnalysisDocument(
                document.id(), document.filename(), document.status(), chunks));
    }
}
