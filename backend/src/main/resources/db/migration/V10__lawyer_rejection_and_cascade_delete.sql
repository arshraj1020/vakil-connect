-- Adds a REJECTED state to lawyer verification (FR-16 decline) and makes
-- user deletion (FR-17 hard delete) safe by cascading through every table
-- that references a user or a lawyer profile.
--
-- REJECTED IS NOT TERMINAL. `rejected` sits alongside `verified`, both
-- false at signup. An admin can set `rejected` true with a reason; the
-- lawyer's own next profile edit (updateCurrentLawyerProfile) clears both
-- fields back to false, which is what puts them back in the pending queue.
-- There is deliberately no separate "resubmit" endpoint - editing the
-- profile IS the resubmission, exactly as it already is for a first-time
-- application.
ALTER TABLE lawyers
    ADD COLUMN rejected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rejection_reason VARCHAR(1000);

-- ---------------------------------------------------------------- cascade --
-- Every one of these FKs previously had NO delete rule (the Postgres
-- default is RESTRICT), so deleting a user threw a foreign-key violation
-- the moment they had a lawyer profile, an appointment, or a review - the
-- admin "Delete" action had nothing safe to call. email_tokens and
-- ai_documents already cascade (V7, V8); this migration brings the rest of
-- the graph in line so DELETE FROM users WHERE id = ? cascades in one
-- statement instead of the service layer having to order six manual
-- deletes itself and risk missing one.
ALTER TABLE lawyers
    DROP CONSTRAINT fk_lawyers_user,
    ADD CONSTRAINT fk_lawyers_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE lawyer_specializations
    DROP CONSTRAINT fk_lawspec_lawyer,
    ADD CONSTRAINT fk_lawspec_lawyer
        FOREIGN KEY (lawyer_id) REFERENCES lawyers (id) ON DELETE CASCADE;
-- fk_lawspec_specialization is left RESTRICT: specializations are shared
-- reference data, not owned by any one lawyer.

ALTER TABLE lawyer_practice_cities
    DROP CONSTRAINT fk_lawpc_lawyer,
    ADD CONSTRAINT fk_lawpc_lawyer
        FOREIGN KEY (lawyer_id) REFERENCES lawyers (id) ON DELETE CASCADE;
-- fk_lawpc_city stays RESTRICT: same reasoning, cities are shared.

ALTER TABLE lawyer_languages
    DROP CONSTRAINT fk_lawlang_lawyer,
    ADD CONSTRAINT fk_lawlang_lawyer
        FOREIGN KEY (lawyer_id) REFERENCES lawyers (id) ON DELETE CASCADE;
-- fk_lawlang_language stays RESTRICT: languages are shared.

ALTER TABLE availabilities
    DROP CONSTRAINT fk_availabilities_lawyer,
    ADD CONSTRAINT fk_availabilities_lawyer
        FOREIGN KEY (lawyer_id) REFERENCES lawyers (id) ON DELETE CASCADE;

ALTER TABLE appointments
    DROP CONSTRAINT fk_appointments_client,
    ADD CONSTRAINT fk_appointments_client
        FOREIGN KEY (client_id) REFERENCES users (id) ON DELETE CASCADE,
    DROP CONSTRAINT fk_appointments_lawyer,
    ADD CONSTRAINT fk_appointments_lawyer
        FOREIGN KEY (lawyer_id) REFERENCES lawyers (id) ON DELETE CASCADE;

ALTER TABLE reviews
    DROP CONSTRAINT fk_reviews_appointment,
    ADD CONSTRAINT fk_reviews_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE,
    DROP CONSTRAINT fk_reviews_client,
    ADD CONSTRAINT fk_reviews_client
        FOREIGN KEY (client_id) REFERENCES users (id) ON DELETE CASCADE,
    DROP CONSTRAINT fk_reviews_lawyer,
    ADD CONSTRAINT fk_reviews_lawyer
        FOREIGN KEY (lawyer_id) REFERENCES lawyers (id) ON DELETE CASCADE;
