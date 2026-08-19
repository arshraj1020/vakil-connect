package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.embedding.Embedding;
import com.arshraj.vakilconnect.ai.embedding.PermanentEmbeddingException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

/**
 * The ONLY class that writes a vector, and the only one that knows pgvector's
 * wire format.
 *
 * WHY JdbcTemplate RATHER THAN JPA. `ai_document_chunks.embedding` is
 * `vector(768)`, a type Hibernate cannot map without a custom UserType for a
 * value AI-2 never reads back. Confining the raw SQL to one small class is a
 * better trade than a UserType that exists to satisfy a framework: the
 * application already does exactly this for `email_tokens`' unmapped columns.
 *
 * BATCHED, IN ONE STATEMENT, INSIDE THE CALLER'S TRANSACTION. This class opens
 * no transaction of its own - it is called from stage 3 of the pipeline, which
 * has already deleted the previous chunks. Either every row lands or none does,
 * so a failure can never leave a document READY with half its chunks.
 */
@Component
public class ChunkEmbeddingWriter {

    /**
     * The `?::vector` cast is required.
     *
     * pgvector accepts a text literal like `[0.1,0.2]` but PostgreSQL will not
     * implicitly coerce a bind parameter's `text` to `vector`; without the cast
     * the insert fails with "column embedding is of type vector but expression
     * is of type character varying". Binding a String and casting in SQL is the
     * standard approach and keeps the driver out of it.
     */
    private static final String INSERT_CHUNK = """
            INSERT INTO ai_document_chunks
                (id, document_id, chunk_index, content, content_hash, char_count, embedding, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector, now())
            """;

    private final JdbcTemplate jdbcTemplate;

    public ChunkEmbeddingWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes every chunk with its embedding.
     *
     * @param chunks     in ascending chunk_index order
     * @param embeddings positionally aligned with {@code chunks}
     * @param expectedDimension the configured width; every vector must match
     * @throws PermanentEmbeddingException if the two lists disagree in size, or
     *         any vector is the wrong width
     */
    public void write(UUID documentId,
                      List<TextChunk> chunks,
                      List<Embedding> embeddings,
                      int expectedDimension) {

        /*
         * VALIDATE EVERYTHING BEFORE WRITING ANYTHING.
         *
         * The database would catch a wrong-width vector itself - `vector(768)`
         * rejects it - but only on the row that carries it, midway through a
         * batch, as a constraint error naming a column rather than a cause. A
         * misaligned list would not be caught at all: it would silently pair
         * chunk 5's text with chunk 6's vector, and the only symptom would be
         * retrieval quietly returning the wrong passage forever.
         *
         * So the check runs first, over the whole batch, and the message names
         * the configuration to fix.
         */
        if (chunks.size() != embeddings.size()) {
            throw new PermanentEmbeddingException(
                    "chunk/embedding count mismatch: " + chunks.size() + " chunks but "
                            + embeddings.size() + " embeddings - they must align positionally");
        }

        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            if (embedding.dimension() != expectedDimension) {
                throw new PermanentEmbeddingException(
                        "embedding at position " + i + " has " + embedding.dimension()
                                + " dimensions but the column is vector(" + expectedDimension
                                + "). The model and vakilconnect.ai.embedding.dimension "
                                + "must agree with the V9 column width.");
            }
        }

        if (chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_CHUNK, chunks, chunks.size(),
                (PreparedStatement statement, TextChunk chunk) -> {
                    Embedding embedding = embeddings.get(chunk.index());

                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, documentId);
                    statement.setInt(3, chunk.index());
                    statement.setString(4, chunk.content());
                    statement.setString(5, chunk.contentHash());
                    statement.setInt(6, chunk.charCount());
                    statement.setString(7, embedding.toPgVectorLiteral());
                });
    }
}
