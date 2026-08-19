-- ============================================================================
-- V9 — Document chunks and embeddings (AI-2)
--
-- Turns a stored document into the retrievable units RAG needs. It holds
-- extracted text split into overlapping chunks, each with a locally-generated
-- embedding. It does NOT hold questions, answers, conversations or citations -
-- those are AI-3.
--
-- ADDITIVE ONLY, like V7 and V8. Nothing existing is dropped or altered, so the
-- previous jar runs against this schema unmodified: Hibernate `validate` checks
-- that mapped entities have their tables and columns and does not object to
-- extra ones. There is no V10__undo - dropping this table would discard hours
-- of local inference, and re-running ingestion rebuilds it from `ai_documents`
-- anyway, which is the real recovery path.
--
-- Timestamps are timestamptz/Instant, following V7 and V8.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. The pgvector extension
--
-- IF NOT EXISTS so the migration is idempotent across environments that may
-- already have it.
--
-- THIS IS THE STATEMENT MOST LIKELY TO FAIL ON A MANAGED DATABASE, and it fails
-- at deploy time rather than at runtime, which is the good direction. It needs:
--
--   * the extension COMPILED AND PRESENT in the server image. `postgres:16-alpine`
--     does not ship it, which is why the test suite moved to
--     `pgvector/pgvector:pg16`.
--   * a role permitted to CREATE EXTENSION. On Neon, pgvector is on the
--     supported list and the project owner may install it; on some managed
--     providers it must be enabled from a console first.
--
-- If this fails on Neon, the fix is to run `CREATE EXTENSION vector;` once as
-- the project owner in the Neon SQL editor and re-run the deploy - not to work
-- around it in application code.
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS vector;


-- ---------------------------------------------------------------------------
-- 2. ai_document_chunks
--
-- OWNERSHIP IS INHERITED, NOT DUPLICATED. There is no user_id column here, and
-- that is a deliberate decision rather than an omission. A chunk belongs to a
-- document and a document belongs to a user; copying the owner onto every chunk
-- would create a second copy of a security-relevant fact that can drift from
-- the first. Every ownership-scoped query joins `ai_documents` instead, which
-- is one index lookup and cannot disagree with itself.
--
-- The cost is a join on the retrieval path in AI-3. If that ever measures badly
-- the answer is a covering index, not a denormalised owner column that a future
-- UPDATE could leave stale.
-- ---------------------------------------------------------------------------

CREATE TABLE ai_document_chunks (
    id            uuid         NOT NULL,
    document_id   uuid         NOT NULL,

    -- 0-based position in the document. Stable across reprocessing because
    -- chunking is deterministic: the same bytes always produce the same chunks
    -- in the same order.
    chunk_index   integer      NOT NULL,

    -- The chunk's text. `text`, not a bounded varchar: chunk size is
    -- configuration (vakilconnect.ai.ingestion.chunk-size) and a value-too-long
    -- error on a config change would abort ingestion mid-document. In
    -- PostgreSQL text and varchar have identical performance.
    content       text         NOT NULL,

    -- Hex SHA-256 of `content`. Lets a re-run detect that nothing changed, and
    -- makes "did chunking drift" answerable without diffing prose.
    content_hash  varchar(64)  NOT NULL,

    -- Characters, not tokens. A token count would need a tokenizer matching
    -- whichever model is configured, and would silently become a lie the moment
    -- the model changed. Characters are exact, free, and enough to reason about
    -- chunk sizing.
    char_count    integer      NOT NULL,

    /*
     * THE EMBEDDING. 768 dimensions, matching nomic-embed-text.
     *
     * THE DIMENSION IS FIXED HERE AND CANNOT BE PARAMETERISED - SQL DDL takes
     * no variables, so `vector(768)` is a literal. It therefore appears twice
     * in this system: here, and as
     * `vakilconnect.ai.embedding.dimension` in application.yaml.
     *
     * Two declarations of one fact is exactly the drift hazard this project
     * avoids elsewhere, so it is closed by a TEST rather than by wishing:
     * AiDocumentChunkSchemaIT reads the column's actual typmod out of
     * pg_attribute and asserts it equals the configured property. Changing one
     * without the other fails the build.
     *
     * CHANGING THE MODEL IS A MIGRATION, NOT A CONFIG EDIT. A different
     * embedding model almost certainly has a different dimension, and existing
     * vectors would be meaningless alongside new ones even if the width
     * matched - so the change is ALTER COLUMN plus a full re-ingest, which is
     * the honest cost of switching models.
     */
    embedding     vector(768)  NOT NULL,

    created_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_ai_document_chunks PRIMARY KEY (id),

    -- ON DELETE CASCADE, matching ai_documents -> users. A chunk is derived
    -- data with no meaning once its document is gone, and leaving orphans would
    -- mean a deleted document's text remained searchable.
    CONSTRAINT fk_ai_document_chunks_document
        FOREIGN KEY (document_id) REFERENCES ai_documents(id) ON DELETE CASCADE,

    -- THE IDEMPOTENCY INVARIANT, enforced by the database rather than by
    -- convention. Reprocessing deletes then re-inserts; if a bug ever let two
    -- runs interleave, this is what stops the document ending up with two
    -- chunk 0s and a retrieval layer quietly returning both.
    CONSTRAINT uq_ai_document_chunks_position
        UNIQUE (document_id, chunk_index),

    CONSTRAINT ck_ai_document_chunks_index_non_negative
        CHECK (chunk_index >= 0),

    -- An empty chunk is not a chunk. The chunker filters them; this is the
    -- backstop, because an empty chunk would embed to noise and pollute
    -- retrieval.
    CONSTRAINT ck_ai_document_chunks_content_present
        CHECK (length(content) > 0 AND char_count > 0),

    CONSTRAINT ck_ai_document_chunks_hash_format
        CHECK (content_hash ~ '^[0-9a-f]{64}$')
);


-- The document-scoped read, which is every read AI-2 performs: fetch or delete
-- a document's chunks in order.
--
-- Also serves the foreign key. Without it, deleting a document would
-- sequentially scan this table - and it is the largest table in the schema by
-- row count.
CREATE INDEX ix_ai_document_chunks_document
    ON ai_document_chunks (document_id, chunk_index);


-- ---------------------------------------------------------------------------
-- 3. NO VECTOR INDEX, DELIBERATELY
--
-- The obvious next line is an HNSW index on `embedding`. It is not here, and
-- the reason is that an ANN index is a TRADE, not a free speed-up:
--
--   * It is APPROXIMATE. It trades recall for speed, and how much recall is
--     lost depends on build parameters (m, ef_construction) tuned against a
--     real corpus and a real query distribution. Neither exists yet - nothing
--     queries these vectors until AI-3.
--
--   * It costs memory and build time proportional to the row count, on a free
--     tier where both are scarce.
--
--   * Below roughly a hundred thousand vectors, an exact scan restricted to one
--     user's documents is fast AND has perfect recall. This project's realistic
--     scale is a few thousand.
--
-- Adding it later is one CREATE INDEX with no data migration and no application
-- change. Adding it now would be choosing parameters by guesswork and calling
-- the guess a design. AI-3 adds it if measurement asks for it.
-- ---------------------------------------------------------------------------
