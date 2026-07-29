-- ============================================================================
-- V5 — Seed the curated specialization vocabulary (Phase 2E)
--
-- Closes a chicken-and-egg problem. `specializations` has been populated
-- find-or-create BY REGISTRATION since V1: the table starts empty, so
-- GET /api/reference/specializations returns nothing on a fresh database, and a
-- picker driven by that endpoint would offer the first lawyer no options at all.
-- Seeding here is what lets the endpoint become authoritative in this phase -
-- resolve-or-REJECT rather than resolve-or-create.
--
-- The list mirrors SPECIALIZATION_OPTIONS in the frontend exactly. That constant
-- was the de facto vocabulary already; it was simply enforced client-side only,
-- so a direct API call could mint anything. After this migration the database is
-- the vocabulary and the constant is a convenience.
--
-- ON CONFLICT DO NOTHING because `specializations.name` is UNIQUE and existing
-- deployments will already hold some of these rows, created by real
-- registrations. This migration must add what is missing without disturbing what
-- is there - rows are already referenced by `lawyer_specializations`.
--
-- Deliberately NO backfill and NO deletion: rows created outside this list by
-- earlier registrations are left exactly as they are, and remain valid for the
-- lawyers already pointing at them.
-- ============================================================================

INSERT INTO specializations (id, created_at, updated_at, name) VALUES
    (gen_random_uuid(), now(), now(), 'Family Law'),
    (gen_random_uuid(), now(), now(), 'Criminal Law'),
    (gen_random_uuid(), now(), now(), 'Civil Law'),
    (gen_random_uuid(), now(), now(), 'Corporate Law'),
    (gen_random_uuid(), now(), now(), 'Property Law'),
    (gen_random_uuid(), now(), now(), 'Tax Law'),
    (gen_random_uuid(), now(), now(), 'Labour Law'),
    (gen_random_uuid(), now(), now(), 'Consumer Law'),
    (gen_random_uuid(), now(), now(), 'Cyber Law'),
    (gen_random_uuid(), now(), now(), 'Intellectual Property')
ON CONFLICT (name) DO NOTHING;
