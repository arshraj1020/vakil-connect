-- ============================================================================
-- V13 -- Subscription coupons
--
-- Percentage-off codes a lawyer can apply at checkout. This migration is
-- SCHEMA ONLY - it deliberately seeds no coupon codes.
--
-- WHY: this repository is public, and Flyway migrations are plain SQL files
-- committed to it. A coupon code is effectively a password (anyone who knows
-- it gets a discount, up to a free subscription for a 100%-off code), so it
-- must never sit in a git-tracked file where anyone browsing the repo on
-- GitHub can read it. Real coupons are created at runtime instead - see
-- CouponBootstrapRunner, which upserts codes from the COUPON_CODES
-- environment variable (set only in Render's dashboard, never committed) the
-- same way AdminBootstrapRunner creates the admin account from ADMIN_EMAIL /
-- ADMIN_PASSWORD.
--
-- discount_percent is 1-100. 100 means "free" - createOrder skips Razorpay
-- entirely for those and activates the subscription directly (see
-- LawyerSubscriptionServiceImpl), since Razorpay does not support a
-- zero-amount order.
-- ============================================================================

CREATE TABLE coupons (
    id                uuid          NOT NULL,
    created_at        timestamp(6)  NOT NULL,
    updated_at        timestamp(6)  NOT NULL,
    code              varchar(40)   NOT NULL,
    discount_percent  integer       NOT NULL,
    active            boolean       NOT NULL DEFAULT true,
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_discount_percent CHECK (discount_percent BETWEEN 1 AND 100)
);

-- Which coupon (if any) applied to a given subscription row, and what
-- discount it granted at the time - kept even if the coupon is later
-- deactivated or its percentage changed, so history stays accurate.
ALTER TABLE lawyer_subscriptions
    ADD COLUMN coupon_code       varchar(40),
    ADD COLUMN discount_percent  integer NOT NULL DEFAULT 0;
