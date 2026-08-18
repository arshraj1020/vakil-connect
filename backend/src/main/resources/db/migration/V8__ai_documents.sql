-- ============================================================================
-- V8 — AI document store (AI-1)
--
-- The first table of the AI feature, and deliberately the boring one. It holds
-- an uploaded file and its lifecycle state; it does NOT hold extracted text,
-- chunks, or embeddings, and there is no `vector` column and no pgvector
-- extension here. Those arrive in AI-2 and are a separate decision that still
-- depends on confirming pgvector is available on the Neon plan.
--
-- ADDITIVE ONLY, like V7. Nothing existing is dropped, altered or tightened.
-- The consequence is the property the rollback plan rests on: the PREVIOUS jar
-- runs against this schema unmodified, because Hibernate `validate` checks that
-- mapped entities have their tables and columns and does not object to extra
-- ones. An application rollback therefore needs no database rollback, and there
-- is no V9__undo — dropping this table would destroy users' uploads.
--
-- Design of record: docs/AI-ARCHITECTURE-AND-ROADMAP.md
--
-- ---------------------------------------------------------------------------
-- TYPE MAPPING NOTE — follows V7, not V1
--
-- V1 documents `LocalDateTime -> timestamp(6)` and V1-V6 follow it. Every
-- timestamp here is `timestamptz`, mapped from java.time.Instant, matching V7's
-- email_tokens.
--
-- The reason V7 gave was that its timestamps decide token validity. Here it is
-- simpler: `created_at` orders a user's document list and `updated_at` records
-- when processing last moved. Neither is security-critical, but a zone-less
-- wall clock has no upside either, and adding a THIRD convention to a schema
-- that already carries two would be the actively worse choice. New tables use
-- timestamptz.
--
-- The corollary is that this entity cannot extend BaseEntity, whose timestamps
-- are LocalDateTime. EmailToken made the same call for the same reason.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- ai_documents
--
-- BYTES LIVE IN POSTGRES, IN A `bytea` COLUMN. This is a deliberate,
-- documented, temporary choice, not an oversight:
--
--   * Render's filesystem is EPHEMERAL. Anything written to local disk vanishes
--     on the next deploy, so "just save it to /tmp" is not an option that
--     survives contact with production.
--   * S3/R2 would be a second service, a second set of credentials and a second
--     failure mode, for a portfolio project whose defining constraint is zero
--     paid infrastructure.
--   * One datastore means an upload is ONE transaction. With object storage the
--     row and the blob can diverge — an orphaned object, or a row pointing at
--     nothing — and reconciling that is real work nobody has asked for yet.
--
-- The cost is honest and bounded: `bytea` is fully read into memory on access,
-- every row inflates logical backups, and Neon's free tier has a storage cap.
-- The application-level size limit (vakilconnect.ai.document.max-file-size) is
-- what keeps that bounded. Moving to object storage later changes this column
-- and the one class that writes it — nothing else, because no read path in the
-- application selects it.
--
-- NOT `oid`/large object. PostgreSQL's large-object API needs explicit
-- lifecycle management (lo_unlink) and rows would leak blobs on delete.
-- Hibernate maps a plain `byte[]` to bytea and `@Lob` to oid, which is exactly
-- why the entity pins the JDBC type explicitly instead of trusting a default.
-- ---------------------------------------------------------------------------

CREATE TABLE ai_documents (
    id              uuid          NOT NULL,
    user_id         uuid          NOT NULL,

    -- SANITISED at the application boundary before it ever arrives here: path
    -- components stripped, control characters removed, Unicode normalised,
    -- length bounded. The raw client-supplied name is never stored.
    --
    -- 255 is the bound the sanitiser enforces, so a value-too-long error is
    -- unreachable rather than merely unlikely.
    filename        varchar(255)  NOT NULL,

    -- THE SERVER'S OWN CONCLUSION, NOT THE CLIENT'S CLAIM. Determined by
    -- inspecting the bytes; the multipart part's Content-Type header is never
    -- persisted and never decides anything. A client that labels an executable
    -- as application/pdf gets rejected, not stored under its own label.
    content_type    varchar(100)  NOT NULL,

    size_bytes      bigint        NOT NULL,

    -- Hex SHA-256 of the stored bytes: 64 characters exactly.
    --
    -- varchar, NOT char(64) — V7 records the reason. char(n) is blank-padded,
    -- has no performance advantage in PostgreSQL, and would not match
    -- Hibernate's varchar mapping for a String field, so `ddl-auto: validate`
    -- would refuse to start.
    sha256          varchar(64)   NOT NULL,

    content         bytea         NOT NULL,

    status          varchar(32)   NOT NULL,

    -- Populated only in the FAILED state, by AI-2. `text` rather than a bounded
    -- varchar because a truncation error while recording WHY something failed
    -- would lose the only diagnostic there is. Same reasoning as V7's
    -- requested_user_agent.
    --
    -- Holds a fixed, developer-written reason. It must never carry document
    -- content or a provider's echo of it.
    failure_reason  text,

    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_ai_documents PRIMARY KEY (id),

    -- ON DELETE CASCADE, matching email_tokens and for the same reason: a
    -- document is meaningless without its owner, and leaving orphaned rows
    -- holding a deleted user's file contents would be a data-retention problem
    -- rather than a referential-integrity nicety.
    --
    -- Contrast the reference tables in V3/V4, which are RESTRICT because those
    -- rows are shared vocabulary owned by nobody.
    CONSTRAINT fk_ai_documents_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Mirrors the Java enum. Adding a state is then a deliberate migration
    -- rather than something that drifts in silently through an enum edit.
    CONSTRAINT ck_ai_documents_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),

    -- An empty upload is rejected by the application, which returns a described
    -- error. This is the backstop for anything that reaches the table by
    -- another route: a zero-byte document is not a document, and AI-2 would
    -- otherwise queue it for extraction and fail on every attempt.
    CONSTRAINT ck_ai_documents_size_positive
        CHECK (size_bytes > 0),

    -- 64 hex characters, always. Cheap, and it catches a truncated or
    -- misencoded hash at write time rather than when someone later tries to
    -- match on it.
    CONSTRAINT ck_ai_documents_sha256_format
        CHECK (sha256 ~ '^[0-9a-f]{64}$')
);


-- The list query, exactly: a user's own documents, newest first. DESC because
-- that is the only order the endpoint offers.
--
-- Also serves the foreign key. Without an index on user_id, deleting a user
-- would sequentially scan this table — and this is the widest table in the
-- schema, so that scan reads every stored file.
CREATE INDEX ix_ai_documents_user_created
    ON ai_documents (user_id, created_at DESC);

-- Content-identity lookups, SCOPED BY USER because the hash of one user's file
-- is not a fact another user is entitled to probe for.
--
-- DELIBERATELY NOT UNIQUE. Deduplicating re-uploads is attractive - AI-2 will
-- spend real CPU embedding each document - but "the same file twice" is not yet
-- a settled policy question: a FAILED upload must be retryable, and whether a
-- deleted-then-re-uploaded file should resurrect or insert is a product
-- decision nobody has made. Storing and indexing the hash keeps every option
-- open; enforcing uniqueness now would pick one by accident and turn a legal
-- retry into a 409.
CREATE INDEX ix_ai_documents_user_sha256
    ON ai_documents (user_id, sha256);

-- The AI-2 work queue: documents waiting to be processed, oldest first.
--
-- Partial, so it indexes only rows a worker would ever claim. READY documents
-- are the overwhelming majority in steady state and none of them belong in a
-- queue index. Same technique as V7's ix_email_tokens_expires.
CREATE INDEX ix_ai_documents_pending
    ON ai_documents (created_at)
    WHERE status = 'PENDING';
