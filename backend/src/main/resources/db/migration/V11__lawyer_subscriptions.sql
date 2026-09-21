-- Adds billing bookkeeping for the Razorpay-based lawyer subscription feature
-- (lawyers pay Rs 500/month or Rs 5500/year to join the platform).
--
-- SCOPE: THIS IS BOOKKEEPING ONLY, NOT ENFORCEMENT. No existing table, column
-- or query is touched. Nothing in lawyer search or booking eligibility reads
-- this table yet - gating public visibility on subscription status is a
-- deliberate follow-up, not part of this migration.
--
-- LIFECYCLE: a row is inserted PENDING the moment an order is created with
-- Razorpay, and is only ever moved to ACTIVE after the payment signature is
-- verified server-side (see LawyerSubscriptionServiceImpl#verifyPayment). A
-- lawyer can accumulate MANY rows over time - each renewal (monthly or
-- yearly) inserts a NEW row rather than updating the old one, so the table is
-- a full history. The "current" subscription for a lawyer is therefore never
-- looked up by a status column alone; it is the most recent row by
-- created_at (see findTopByLawyerIdOrderByCreatedAtDesc).
CREATE TABLE lawyer_subscriptions (
    id                    uuid           NOT NULL,
    created_at            timestamp(6)   NOT NULL,
    updated_at            timestamp(6)   NOT NULL,
    lawyer_id             uuid           NOT NULL,
    plan                  varchar(20)    NOT NULL,
    status                varchar(20)    NOT NULL,
    razorpay_order_id     varchar(255),
    razorpay_payment_id   varchar(255),
    razorpay_signature    varchar(512),
    amount_paise          integer        NOT NULL,
    currency              varchar(10)    NOT NULL DEFAULT 'INR',
    starts_at             timestamp(6),
    expires_at            timestamp(6),
    CONSTRAINT pk_lawyer_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_lawyer_subscriptions_lawyer FOREIGN KEY (lawyer_id)
        REFERENCES lawyers (id) ON DELETE CASCADE
);

-- Every lookup in the service (current status, ownership check on verify) is
-- keyed by lawyer_id, so this is the one index the feature actually needs.
CREATE INDEX idx_lawyer_subscriptions_lawyer_id ON lawyer_subscriptions (lawyer_id);
