-- ============================================================================
-- V6 — Backfill reference links (Phase 2F)
--
-- Populates the nullable reference columns added in V4 from the legacy data
-- that already exists. Legacy columns are NOT modified and NOT dropped; they
-- remain the source of truth until the read cut-over.
--
-- ---------------------------------------------------------------------------
-- WHAT CAN ACTUALLY BE BACKFILLED
--
-- Of the five reference targets, only two have a legacy source to read from:
--
--   lawyers.primary_city_id      <- lawyers.city          BACKFILLED BELOW
--   lawyer_practice_cities       <- primary_city_id       BACKFILLED BELOW
--
--   users.city_id                <- (no source column)    NOT BACKFILLABLE
--   users.preferred_language_id  <- (no source column)    NOT BACKFILLABLE
--   lawyer_languages             <- (no source column)    NOT BACKFILLABLE
--
-- `users` has never held a city or language, and `lawyers` has never held a
-- language: those V4 columns are forward-looking, for onboarding fields that do
-- not exist yet. There is nothing to migrate, so this migration does not touch
-- them - inventing values would be exactly the guessing this phase forbids.
--
-- In particular, users.city_id is NOT derived from the lawyer's practice city.
-- Where someone practises is not where they live, and that inference would be
-- indistinguishable from real data once written.
--
-- The reconciliation service reports all five, so the three unpopulated targets
-- stay visible rather than being quietly forgotten.
--
-- ---------------------------------------------------------------------------
-- IDEMPOTENCY
--
-- Every statement is guarded:
--   * the UPDATEs touch only rows WHERE primary_city_id IS NULL, so a valid
--     existing link is never overwritten and a second run is a no-op
--   * the INSERT uses ON CONFLICT DO NOTHING against the composite primary key
--
-- Running this twice produces the same database.
--
-- ---------------------------------------------------------------------------
-- NORMALISATION
--
-- lower + trim + collapse-internal-whitespace, matching TextNormalizer for
-- ASCII input. TextNormalizer additionally strips diacritics (NFD, drop
-- combining marks); reproducing that in SQL would need the `unaccent`
-- extension, and a second extension dependency is not worth it here:
--
--   * `lawyers.city` is operator-entered free text on an India-first platform
--     and is ASCII in practice
--   * a value that does not match is left NULL and surfaces in the
--     reconciliation report, which is the designed escape hatch
--
-- So the failure mode is under-linking, which is visible and fixable - never
-- mis-linking, which is neither.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. Exact match on the canonical city name.
--
-- `match_count = 1` is the never-guess guard. City names are unique
-- country-wide today, but uniqueness is only ENFORCED per state, so a name that
-- ever resolves to two cities must resolve to neither rather than have one
-- picked arbitrarily by the join.
--
-- Window functions are used instead of MIN/MAX because PostgreSQL
-- does not define aggregate MIN/MAX for UUID. The COUNT(...) = 1
-- invariant guarantees exactly one surviving city, so the selected
-- city_id is identical to the original grouped formulation.
-- ---------------------------------------------------------------------------
WITH candidate AS (
    SELECT id,
           lower(regexp_replace(btrim(city), '\s+', ' ', 'g')) AS normalized
    FROM lawyers
    WHERE primary_city_id IS NULL
      AND city IS NOT NULL
),
scored AS (
    SELECT c.id AS lawyer_id,
           ct.id AS city_id,
           count(*) OVER (PARTITION BY c.id) AS match_count
    FROM candidate c
    JOIN cities ct
      ON ct.active
     AND ct.name_normalized = c.normalized
),
matched AS (
    SELECT lawyer_id, city_id
    FROM scored
    WHERE match_count = 1
)
UPDATE lawyers l
SET primary_city_id = m.city_id
FROM matched m
WHERE l.id = m.lawyer_id
  AND l.primary_city_id IS NULL;


-- ---------------------------------------------------------------------------
-- 2. Alias match, for anything the exact pass could not resolve.
--
-- This is what maps the historical names still in daily use - Bombay, Calcutta,
-- Bangalore, Gurgaon - onto their current cities. Without it a large share of
-- legacy free text would be left unmapped for no good reason.
--
-- Same single-match guard: one alias can legitimately point at several cities
-- in different states, and an ambiguous alias resolves to nothing.
--
-- This guard counts DISTINCT CITIES, not rows: two alias spellings pointing at
-- the same city are not an ambiguity. COUNT(DISTINCT ...) OVER () is not
-- implemented in PostgreSQL, so alias_target deduplicates first - once the
-- pairs are distinct, a row count per lawyer IS the distinct-city count.
-- ---------------------------------------------------------------------------
WITH candidate AS (
    SELECT id,
           lower(regexp_replace(btrim(city), '\s+', ' ', 'g')) AS normalized
    FROM lawyers
    WHERE primary_city_id IS NULL
      AND city IS NOT NULL
),
-- DISTINCT collapses several alias spellings that point at the SAME city into
-- one row, so the row count below is a count of distinct CITIES - which is what
-- the guard has always measured.
alias_target AS (
    SELECT DISTINCT
           c.id AS lawyer_id,
           ca.city_id AS city_id
    FROM candidate c
    JOIN city_aliases ca
      ON ca.alias_normalized = c.normalized
    JOIN cities ct
      ON ct.id = ca.city_id
     AND ct.active
),
scored AS (
    SELECT lawyer_id,
           city_id,
           count(*) OVER (PARTITION BY lawyer_id) AS city_count
    FROM alias_target
),
matched AS (
    SELECT lawyer_id, city_id
    FROM scored
    WHERE city_count = 1
)
UPDATE lawyers l
SET primary_city_id = m.city_id
FROM matched m
WHERE l.id = m.lawyer_id
  AND l.primary_city_id IS NULL;


-- ---------------------------------------------------------------------------
-- 3. Maintain the Option C invariant: a primary city is always a member of the
--    practice set, so search can query one table without special-casing it.
--
-- Applies to every lawyer with a primary city, not only those linked above -
-- rows written by the Phase 2E dual-write already satisfy this, and the
-- ON CONFLICT makes re-asserting it free.
-- ---------------------------------------------------------------------------
INSERT INTO lawyer_practice_cities (lawyer_id, city_id)
SELECT l.id, l.primary_city_id
FROM lawyers l
WHERE l.primary_city_id IS NOT NULL
ON CONFLICT (lawyer_id, city_id) DO NOTHING;
