package com.arshraj.vakilconnect.ai.rag;

import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.embedding.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cosine similarity search over pgvector, scoped to one user.
 *
 * ================== THE OWNERSHIP GUARANTEE IS IN THE SQL ==================
 *
 * The WHERE clause joins `ai_documents` and filters on `user_id` BEFORE any
 * ranking happens. Another user's chunks are never scored, never ordered and
 * never returned - they are not in the result set to be filtered out later.
 *
 * That matters more here than anywhere else in the application. Everything
 * downstream of this query - context building, the prompt, the model, the
 * citations - operates on whatever this returns, and none of it re-checks
 * ownership. It cannot: a language model has no way to know who is asking, and
 * asking it to "only use documents belonging to user X" would be an
 * authorization decision delegated to a text generator. So this query is the
 * single boundary, and it is enforced by the database.
 *
 * WHY JdbcTemplate. `<=>` is a pgvector operator with no JPQL equivalent, and
 * `vector` has no Hibernate mapping - the same reason ChunkEmbeddingWriter
 * writes with JdbcTemplate. Raw SQL is confined to these two classes.
 */
@Component
public class PgVectorDocumentRetriever implements DocumentRetriever {

    private static final Logger log = LoggerFactory.getLogger(PgVectorDocumentRetriever.class);

    /**
     * COSINE DISTANCE (`<=>`), not L2 (`<->`) or inner product (`<#>`).
     *
     * nomic-embed-text produces direction-encoded embeddings where semantic
     * similarity is angle, not magnitude. L2 would let a long passage and a
     * short one about the same clause look far apart purely because one vector
     * is longer. Cosine is also what the model's own training objective
     * optimises, so it is the metric the numbers were built for.
     *
     * THE DISTANCE IS COMPUTED ONCE, in the subquery, then filtered and ordered
     * on the alias. Repeating `c.embedding <=> ?` in WHERE and ORDER BY would
     * bind the vector twice and invite the two copies to drift apart during a
     * later edit - which would silently rank by one thing and filter by another.
     *
     * ORDERED BY DISTANCE THEN chunk_index. The tiebreaker is not decoration:
     * without it, two chunks at identical distance come back in whatever order
     * the scan produced, so the same question could cite different sources on
     * consecutive calls. Deterministic output is what makes retrieval testable.
     *
     * STATUS = READY. A document mid-reprocessing still holds its PREVIOUS
     * chunks, and answering from superseded text without saying so is worse
     * than saying there is not enough evidence yet. Not a security filter -
     * those rows belong to the same user - a freshness one.
     */
    private static final String SEARCH = """
            SELECT chunk_id, document_id, document_name, chunk_index, content, distance
              FROM (
                    SELECT c.id            AS chunk_id,
                           c.document_id   AS document_id,
                           d.filename      AS document_name,
                           c.chunk_index   AS chunk_index,
                           c.content       AS content,
                           c.embedding <=> CAST(? AS vector) AS distance
                      FROM ai_document_chunks c
                      JOIN ai_documents d ON d.id = c.document_id
                     WHERE d.user_id = ?
                       AND d.status  = ?
                   ) scored
             WHERE distance <= ?
             ORDER BY distance ASC, chunk_index ASC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AiRetrievalProperties properties;

    public PgVectorDocumentRetriever(JdbcTemplate jdbcTemplate, AiRetrievalProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(UUID ownerId, Embedding queryEmbedding) {
        if (ownerId == null || queryEmbedding == null) {
            // A null owner would be a query with no ownership predicate. Refuse
            // rather than let it reach the database.
            throw new IllegalArgumentException("owner and query embedding are both required");
        }

        List<RetrievedChunk> chunks = jdbcTemplate.query(SEARCH,
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getObject("chunk_id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("document_name"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("distance")),
                queryEmbedding.toPgVectorLiteral(),
                ownerId,
                AiDocumentStatus.READY.name(),
                properties.maxDistance(),
                properties.topK());

        // Counts and distances only. Never the retrieved text, never the owner.
        log.debug("Retrieved {} chunk(s) within distance {} (topK {})",
                chunks.size(), properties.maxDistance(), properties.topK());

        return chunks;
    }
}
