-- ============================================================================
-- V2 — Prevent double booking (appointment slot race condition)
--
-- Enforces, at the database level, that a lawyer can have at most ONE active
-- appointment for a given (date, time). "Active" = the statuses that actually
-- occupy the slot, derived from the appointment lifecycle in code:
--
--   book    -> PENDING
--   accept  -> ACCEPTED
--   reject  -> REJECTED    (terminal, frees the slot)
--   cancel  -> CANCELLED   (terminal, frees the slot)
--   complete-> COMPLETED   (terminal, frees the slot)
--
-- So only PENDING and ACCEPTED reserve the slot. A partial unique index lets a
-- slot be re-booked after a prior appointment was rejected/cancelled/completed,
-- while making the second concurrent active insert fail atomically.
--
-- Not expressible as a JPA @UniqueConstraint (partial index), so it lives only
-- in the database. Hibernate `validate` ignores indexes, so this is compatible.
-- ============================================================================

CREATE UNIQUE INDEX uq_appointments_active_slot
    ON appointments (lawyer_id, appointment_date, appointment_time)
    WHERE status IN ('PENDING', 'ACCEPTED');
