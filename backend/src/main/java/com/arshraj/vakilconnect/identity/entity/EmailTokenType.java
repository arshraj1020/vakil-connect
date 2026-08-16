package com.arshraj.vakilconnect.identity.entity;

/**
 * The two flows served by the single {@code email_tokens} table.
 *
 * These names are load-bearing: they are persisted as strings and mirrored by
 * the database CHECK constraint {@code ck_email_tokens_type}, which V7 already
 * applied to production. Renaming a constant here without a migration would
 * make every subsequent insert fail the constraint.
 */
public enum EmailTokenType {

    /** Proves control of the mailbox after registration. */
    VERIFY_EMAIL,

    /** Authorises a password change without knowing the current password. */
    RESET_PASSWORD
}
