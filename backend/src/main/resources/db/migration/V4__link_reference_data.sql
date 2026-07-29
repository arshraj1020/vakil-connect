-- ============================================================================
-- V4 — Link reference data to users and lawyers (Phase 2B)
--
-- Adds the associations only. Nothing is populated, nothing is read, and the
-- existing free-text `lawyers.city` column is untouched and still authoritative.
--
-- EVERY new column is NULLABLE. That is not a stylistic preference: `users` and
-- `lawyers` already hold rows, and adding a NOT NULL column to a populated table
-- fails outright. This project has already hit that once - "column \"active\" of
-- relation \"users\" contains null values" - so the rule here is nullable first,
-- backfill second, tighten third, in three separate migrations.
--
-- Tightening `primary_city_id` to NOT NULL and dropping `lawyers.city` happens
-- only after the backfill phase reports zero unmapped rows.
--
-- Referential behaviour:
--
--   * FKs onto reference tables are ON DELETE RESTRICT, matching V3. Reference
--     rows are deactivated, never deleted, and a city with lawyers attached must
--     not be removable.
--
--   * Join-table FKs onto `lawyers` carry no ON DELETE clause, matching the
--     existing `lawyer_specializations` convention in V1. JPA removes the join
--     rows itself when a lawyer's collection changes or the lawyer is deleted.
--
-- Option C is modelled here: a single `primary_city_id` for display, plus a
-- `lawyer_practice_cities` join table for search. The invariant that the primary
-- city is ALSO a member of the practice set is a service-layer rule and is NOT
-- enforced in this phase - there is no service-layer code in Phase 2B. Until it
-- lands, the two are independent at the persistence layer.
-- ============================================================================

-- ------------------------------------------------------------------- lawyers

-- Display location. Nullable until the backfill phase populates it.
ALTER TABLE lawyers
    ADD COLUMN primary_city_id uuid;

ALTER TABLE lawyers
    ADD CONSTRAINT fk_lawyers_primary_city
        FOREIGN KEY (primary_city_id) REFERENCES cities (id) ON DELETE RESTRICT;

-- Search axis. Every city a lawyer practises in, including the primary one.
CREATE TABLE lawyer_practice_cities (
    lawyer_id  uuid  NOT NULL,
    city_id    uuid  NOT NULL,
    CONSTRAINT pk_lawyer_practice_cities PRIMARY KEY (lawyer_id, city_id),
    CONSTRAINT fk_lawpc_lawyer FOREIGN KEY (lawyer_id) REFERENCES lawyers (id),
    CONSTRAINT fk_lawpc_city FOREIGN KEY (city_id) REFERENCES cities (id)
        ON DELETE RESTRICT
);

CREATE TABLE lawyer_languages (
    lawyer_id    uuid  NOT NULL,
    language_id  uuid  NOT NULL,
    CONSTRAINT pk_lawyer_languages PRIMARY KEY (lawyer_id, language_id),
    CONSTRAINT fk_lawlang_lawyer FOREIGN KEY (lawyer_id) REFERENCES lawyers (id),
    CONSTRAINT fk_lawlang_language FOREIGN KEY (language_id) REFERENCES languages (id)
        ON DELETE RESTRICT
);

-- --------------------------------------------------------------------- users

ALTER TABLE users
    ADD COLUMN city_id uuid;

ALTER TABLE users
    ADD CONSTRAINT fk_users_city
        FOREIGN KEY (city_id) REFERENCES cities (id) ON DELETE RESTRICT;

ALTER TABLE users
    ADD COLUMN preferred_language_id uuid;

ALTER TABLE users
    ADD CONSTRAINT fk_users_preferred_language
        FOREIGN KEY (preferred_language_id) REFERENCES languages (id) ON DELETE RESTRICT;

-- ------------------------------------------------------------------- indexes
--
-- The practice-city index is the one that matters: lawyer search becomes a
-- semi-join against it, replacing the free-text equality on `lawyers.city`.
-- The reverse direction (city -> lawyers) is the direction search reads.

CREATE INDEX idx_lawyer_practice_cities_city  ON lawyer_practice_cities (city_id);
CREATE INDEX idx_lawyer_languages_language    ON lawyer_languages (language_id);
CREATE INDEX idx_lawyers_primary_city         ON lawyers (primary_city_id);
CREATE INDEX idx_users_city                   ON users (city_id);
CREATE INDEX idx_users_preferred_language     ON users (preferred_language_id);
