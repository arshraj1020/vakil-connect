-- ============================================================================
-- V7 — Email verification and password reset (Identity Phase 1)
--
-- Schema only. No application code reads `email_tokens` yet and nothing emits
-- or checks the JWT claim that `credentials_changed_at` will back. Shipping the
-- schema on its own means the only irreversible statement in the whole feature
-- (step 2) can be applied to a restored snapshot and verified days before any
-- behaviour depends on it.
--
-- ADDITIVE ONLY. No column is dropped, no type is changed, nothing existing is
-- tightened. The consequence is the property this feature's rollback plan rests
-- on: the PREVIOUS jar runs against this schema unmodified, because Hibernate
-- `validate` checks that mapped entities have their tables and columns and does
-- not object to extra ones. An application rollback therefore needs no database
-- rollback, and there is deliberately no V8__undo — dropping the table would
-- destroy audit history and nothing here needs undoing.
--
-- Design of record: backend/docs/IDENTITY-TDD.md
-- Phase plan:      backend/docs/IDENTITY-ROADMAP.md
--
-- ---------------------------------------------------------------------------
-- TYPE MAPPING NOTE — this migration departs from the V1 header
--
-- V1 documents `LocalDateTime -> timestamp(6)` and every column in V1-V6
-- follows it. Every timestamp added here is `timestamptz`, mapped from
-- java.time.Instant.
--
-- The reason is that these columns are compared against "now" to decide whether
-- a security token is still valid. A wall-clock timestamp with no zone silently
-- shifts meaning across a DST boundary or a host in another region, and the
-- failure mode is a token that lives an hour longer than intended. `created_at`
-- on other tables is only ever displayed, so it never had that problem.
--
-- The cost is that `users` now carries both conventions: `created_at` stays
-- `timestamp(6)` (written by BaseEntity from LocalDateTime.now(), JVM-local)
-- while `credentials_changed_at` is `timestamptz`. That is a deliberate,
-- documented inconsistency, not an oversight — converting the existing columns
-- is a separate migration and a separate decision.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. users.credentials_changed_at
--
-- The anchor for JWT invalidation. A token carries the value that was current
-- when it was issued; once a password changes this column moves forward and
-- every token minted before it stops being accepted. Without it a password
-- reset does not end an attacker's session — the stolen token stays valid until
-- it expires, up to JWT_EXPIRATION (24h by default).
--
-- NOT NULL WITH A DEFAULT, IN ONE STATEMENT. V4's header records this project's
-- rule — "nullable first, backfill second, tighten third" — after the failure
-- `column "active" of relation "users" contains null values`. That rule exists
-- for NOT NULL *without* a default. With one, PostgreSQL fills every row as
-- part of the same statement, so the rule's purpose is satisfied rather than
-- bypassed.
--
-- now() is STABLE, not volatile, so PostgreSQL 11+ evaluates it once and stores
-- it in pg_attribute.attmissingval instead of rewriting the table. This step is
-- metadata-only regardless of row count.
--
-- Backdating to created_at was considered and rejected: it would leave tokens
-- issued before this deploy valid against a mechanism that had never run in
-- production. Defaulting to now() invalidates them, which is the safe direction
-- and costs one forced sign-out — and not even at this deploy, since nothing
-- reads the value until the phase that adds the claim.
--
-- The DEFAULT is left in place after the migration. Hibernate always includes
-- the column in its INSERTs so the application never relies on it, but an
-- ad-hoc INSERT during an incident should not be able to produce a row that
-- silently invalidates nothing.
-- ---------------------------------------------------------------------------

ALTER TABLE users
    ADD COLUMN credentials_changed_at timestamptz NOT NULL DEFAULT now();


-- ---------------------------------------------------------------------------
-- 2. Grandfather every existing account
--
-- `is_email_verified` shipped in V1 but nothing ever wrote it except
-- AdminBootstrapRunner, so every self-registered user currently reads false.
-- Enabling the login gate without this backfill would lock out the entire
-- existing user base for a policy that did not exist when they signed up.
--
-- THIS IS THE ONLY IRREVERSIBLE STATEMENT IN THE FEATURE. Take a backup before
-- deploying. It is also the only one that rewrites rows; at the current scale
-- that is immaterial, but note that the ALTER above holds ACCESS EXCLUSIVE on
-- `users` until this transaction commits, so on a very large table this is the
-- step that would need batching outside the migration.
--
-- Deliberately NOT scoped by created_at. Flyway's checksum and history table
-- are the guarantee that this runs exactly once; a created_at predicate would
-- have to hardcode an environment-specific timestamp to mean anything, and a
-- comment claiming protection the SQL does not provide is worse than no comment.
--
-- Users who register AFTER this migration but BEFORE verification emails go
-- live are not covered here and cannot be — they do not exist yet. Catching
-- that cohort is an operational step on the cut-over checklist, not a schema
-- concern. See IDENTITY-ROADMAP.md, "Cut-over".
-- ---------------------------------------------------------------------------

UPDATE users
   SET is_email_verified = true
 WHERE is_email_verified = false;


-- ---------------------------------------------------------------------------
-- 3. email_tokens
--
-- One table serving both VERIFY_EMAIL and RESET_PASSWORD. The columns and the
-- lifecycle are identical, so splitting them would duplicate the consume and
-- expire logic to satisfy a purity instinct.
--
-- NO updated_at, AND NO BaseEntity. Every other table in this schema carries
-- created_at + updated_at from the BaseEntity mapped superclass. This one does
-- not, for two reasons: a token is immutable once issued except for reaching a
-- terminal state, and used_at / invalidated_at ARE that record with more
-- information than a generic updated_at would carry; and BaseEntity's
-- created_at is LocalDateTime, which is the wrong type here (see the header).
--
-- token_hash is varchar, NOT char(64). char(n) is blank-padded, has no
-- performance advantage in PostgreSQL, and would not match Hibernate's varchar
-- mapping for a String field — `ddl-auto: validate` would fail on it.
--
-- The raw token is never stored. It exists in memory for the length of one
-- request and is then only recoverable from the user's inbox.
--
-- requested_user_agent is `text` rather than a bounded varchar: real UA strings
-- routinely exceed 255 characters, and a value-too-long error here would abort
-- the enclosing registration transaction. In PostgreSQL text and varchar have
-- identical performance, so the bound would buy nothing but an outage.
--
-- IP columns are varchar(45), not inet: the values are only ever logged and
-- displayed, never subnet-matched, and inet would force a Hibernate custom type
-- for no gain. 45 characters is the longest possible IPv6 form.
-- ---------------------------------------------------------------------------

CREATE TABLE email_tokens (
    id                    uuid         NOT NULL,
    user_id               uuid         NOT NULL,
    type                  varchar(32)  NOT NULL,
    token_hash            varchar(64)  NOT NULL,
    expires_at            timestamptz  NOT NULL,
    used_at               timestamptz,
    invalidated_at        timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    requested_ip          varchar(45),
    requested_user_agent  text,
    consumed_ip           varchar(45),

    CONSTRAINT pk_email_tokens PRIMARY KEY (id),

    -- ON DELETE CASCADE: a token is meaningless without its user, and the only
    -- thing it holds is a hash. Contrast the reference tables in V3/V4, which
    -- are RESTRICT because those rows are shared vocabulary.
    CONSTRAINT fk_email_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Mirrors the Java enum. Adding a value here is a deliberate migration
    -- rather than something that can drift in silently.
    CONSTRAINT ck_email_tokens_type
        CHECK (type IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),

    -- The two terminal states are mutually exclusive: a token was either
    -- consumed by the user or superseded by us, never both. Keeping them in
    -- separate columns preserves that distinction for audit and for the
    -- click-through metric; this CHECK stops the pair reaching a state that
    -- would make neither meaning trustworthy.
    CONSTRAINT ck_email_tokens_terminal_state
        CHECK (used_at IS NULL OR invalidated_at IS NULL)
);


-- Lookup path. Consumption hashes the presented token and probes this index.
-- UNIQUE because two rows sharing a hash would be a correctness bug, not a
-- capacity problem — at 256 bits of entropy a natural collision will not happen,
-- so a duplicate could only come from a defect.
CREATE UNIQUE INDEX uq_email_tokens_hash
    ON email_tokens (token_hash);

-- THE CORE INVARIANT: at most one live token per (user, type).
--
-- This makes "invalidate the old one before issuing a new one" a database rule
-- rather than a service-layer convention, so two concurrent resend requests
-- cannot both succeed and leave two working links in the user's inbox. The
-- second insert fails on this index and the service translates that into a
-- cooldown response.
--
-- Partial, so the constraint applies only to live rows: consumed and superseded
-- tokens accumulate freely as an audit trail. Same technique as V2's
-- uq_appointments_active_slot, and like that one it is not expressible as a JPA
-- @UniqueConstraint, so it lives only here. Hibernate `validate` ignores
-- indexes, so this is compatible.
CREATE UNIQUE INDEX uq_email_tokens_live
    ON email_tokens (user_id, type)
    WHERE used_at IS NULL AND invalidated_at IS NULL;

-- Cooldown check: the newest request for a (user, type), regardless of whether
-- it is still live. DESC because the query only ever wants the latest row.
-- Also serves the FK, which would otherwise have no index of its own and would
-- make deleting a user a sequential scan of this table.
CREATE INDEX ix_email_tokens_user_type_created
    ON email_tokens (user_id, type, created_at DESC);

-- Purge job. Partial for the same reason as above: it only ever sweeps rows
-- that are still live and have aged out, so terminal rows do not need to be in
-- the index.
CREATE INDEX ix_email_tokens_expires
    ON email_tokens (expires_at)
    WHERE used_at IS NULL AND invalidated_at IS NULL;
