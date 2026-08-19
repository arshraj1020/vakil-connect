package com.arshraj.vakilconnect.ai.document;

import com.arshraj.vakilconnect.ai.embedding.AiEmbeddingProperties;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9 asserted against the REAL PostgreSQL, not against JPA annotations.
 *
 * The distinction is the whole point of this class. An entity can be annotated
 * perfectly and still map to a column that does not exist, has the wrong type,
 * or has lost a constraint - `ddl-auto: validate` checks that mapped columns
 * are PRESENT, not that indexes, foreign keys, check constraints or a vector's
 * WIDTH are what the design says. Those live only in the migration, so they can
 * only be verified by asking the database.
 */
@DisplayName("V9 ai_document_chunks schema")
class AiDocumentChunkSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiEmbeddingProperties embeddingProperties;

    @Test
    @DisplayName("the pgvector extension is installed")
    void vectorExtensionExists() {
        /*
         * If this fails, the Testcontainers image is wrong. pgvector is a
         * compiled C extension: `CREATE EXTENSION vector` in V9 cannot succeed
         * on postgres:16-alpine, which is why AbstractIntegrationTest moved to
         * pgvector/pgvector:pg16. There is no runtime install and no pure-SQL
         * workaround.
         */
        Integer installed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);

        assertEquals(1, installed, "the `vector` extension is not installed");
    }

    @Test
    @DisplayName("the table exists with every column V9 declares")
    void tableHasExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_name = 'ai_document_chunks'
                 ORDER BY column_name
                """, String.class);

        assertTrue(columns.containsAll(List.of(
                        "id", "document_id", "chunk_index", "content",
                        "content_hash", "char_count", "embedding", "created_at")),
                "missing columns; found " + columns);

        // NO user_id, and that is a design decision rather than an omission:
        // ownership is inherited through ai_documents so there is only ever one
        // copy of a security-relevant fact.
        assertTrue(columns.stream().noneMatch(c -> c.contains("user")),
                "ownership must be inherited through ai_documents, not duplicated: " + columns);
    }

    @Test
    @DisplayName("the embedding column is a real PostgreSQL `vector`, not text or bytea")
    void embeddingColumnIsVector() {
        // information_schema reports extension types as USER-DEFINED, so the
        // actual type name comes from pg_type via udt_name.
        String udtName = jdbcTemplate.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                 WHERE table_name = 'ai_document_chunks' AND column_name = 'embedding'
                """, String.class);

        assertEquals("vector", udtName,
                "the embedding column must be pgvector's type; text or bytea would "
                        + "store the numbers but make similarity search impossible");
    }

    @Test
    @DisplayName("THE DIMENSION IN THE DATABASE AND IN CONFIGURATION CANNOT DRIFT")
    void vectorDimensionMatchesConfiguration() {
        /*
         * THE TEST THIS CLASS EXISTS FOR.
         *
         * SQL DDL takes no variables, so `vector(768)` is a literal in V9 while
         * `vakilconnect.ai.embedding.dimension` is a property - two declarations
         * of one fact, which is exactly the drift hazard this project closes
         * everywhere else.
         *
         * pgvector stores the width in atttypmod, the same slot varchar uses for
         * its length. Reading it and comparing against the bound property means
         * changing either one alone FAILS THE BUILD - correctly, because a new
         * dimension needs an ALTER COLUMN and a full re-ingest of every
         * document, not a config edit.
         */
        Integer databaseDimension = jdbcTemplate.queryForObject("""
                SELECT a.atttypmod
                  FROM pg_attribute a
                  JOIN pg_class c ON c.oid = a.attrelid
                 WHERE c.relname = 'ai_document_chunks'
                   AND a.attname = 'embedding'
                   AND a.attnum > 0
                """, Integer.class);

        assertNotNull(databaseDimension, "embedding column not found in pg_attribute");
        assertEquals(768, databaseDimension,
                "V9 must declare vector(768) to match nomic-embed-text");
        assertEquals(embeddingProperties.dimension(), databaseDimension,
                "vakilconnect.ai.embedding.dimension (" + embeddingProperties.dimension()
                        + ") disagrees with the vector(n) width in V9 (" + databaseDimension
                        + "). Changing one requires changing the other AND re-ingesting.");
    }

    @Test
    @DisplayName("the foreign key to ai_documents exists and CASCADEs")
    void foreignKeyExistsWithCascade() {
        // CASCADE matters: a chunk is derived data with no meaning once its
        // document is gone, and orphans would leave a deleted document's text
        // searchable.
        String deleteRule = jdbcTemplate.queryForObject("""
                SELECT rc.delete_rule
                  FROM information_schema.referential_constraints rc
                  JOIN information_schema.table_constraints tc
                    ON tc.constraint_name = rc.constraint_name
                 WHERE tc.table_name = 'ai_document_chunks'
                   AND tc.constraint_type = 'FOREIGN KEY'
                """, String.class);

        assertEquals("CASCADE", deleteRule);
    }

    @Test
    @DisplayName("UNIQUE (document_id, chunk_index) exists — the idempotency invariant")
    void uniqueConstraintExists() {
        /*
         * Enforced by the DATABASE rather than by convention. Reprocessing
         * deletes then re-inserts; if a bug ever let two runs interleave, this
         * is what stops a document ending up with two chunk 0s and a retrieval
         * layer quietly returning both.
         */
        Integer found = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM pg_constraint c
                  JOIN pg_class t ON t.oid = c.conrelid
                 WHERE t.relname = 'ai_document_chunks'
                   AND c.contype = 'u'
                   AND pg_get_constraintdef(c.oid) LIKE '%document_id%chunk_index%'
                """, Integer.class);

        assertEquals(1, found, "UNIQUE (document_id, chunk_index) is missing");
    }

    @Test
    @DisplayName("the CHECK constraints V9 declares are all present")
    void checkConstraintsExist() {
        List<String> checks = jdbcTemplate.queryForList("""
                SELECT c.conname FROM pg_constraint c
                  JOIN pg_class t ON t.oid = c.conrelid
                 WHERE t.relname = 'ai_document_chunks' AND c.contype = 'c'
                """, String.class);

        assertTrue(checks.contains("ck_ai_document_chunks_index_non_negative"), "" + checks);
        assertTrue(checks.contains("ck_ai_document_chunks_content_present"), "" + checks);
        assertTrue(checks.contains("ck_ai_document_chunks_hash_format"), "" + checks);
    }

    @Test
    @DisplayName("the document lookup index exists")
    void documentIndexExists() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes WHERE tablename = 'ai_document_chunks'
                """, String.class);

        assertTrue(indexes.contains("ix_ai_document_chunks_document"), "" + indexes);
    }

    @Test
    @DisplayName("NO vector index yet — deliberately, and asserted so it stays a decision")
    void noVectorIndexYet() {
        /*
         * An HNSW index is APPROXIMATE: it trades recall for speed, and its
         * build parameters need a real corpus and a real query distribution to
         * tune. Neither exists until AI-3, and below ~100k vectors an exact
         * scan scoped to one user is fast AND has perfect recall.
         *
         * Asserted rather than merely commented so that adding one is a
         * deliberate change with a failing test to explain itself, not
         * something that appears in a refactor.
         */
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexdef FROM pg_indexes WHERE tablename = 'ai_document_chunks'
                """, String.class);

        assertTrue(indexes.stream().noneMatch(
                        d -> d.contains("hnsw") || d.contains("ivfflat")),
                "a vector index appeared without a decision: " + indexes);
    }

    @Test
    @DisplayName("V9 applied cleanly and Flyway recorded it as successful")
    void migrationApplied() {
        // ddl-auto: validate already refuses to start on a mismatch, so reaching
        // this assertion is most of the proof; the history row makes it explicit.
        Integer applied = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                 WHERE version = '9' AND success = true
                """, Integer.class);

        assertEquals(1, applied, "V9 is not recorded as a successful migration");
    }
}
