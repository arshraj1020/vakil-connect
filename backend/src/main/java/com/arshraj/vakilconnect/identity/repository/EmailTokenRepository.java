package com.arshraj.vakilconnect.identity.repository;

import com.arshraj.vakilconnect.identity.entity.EmailToken;
import com.arshraj.vakilconnect.identity.entity.EmailTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    /**
     * Consumption. THE most important query in this feature.
     *
     * The predicate IS the guard and the write is the same statement, so there
     * is no window between checking that a token is usable and marking it used.
     * Two concurrent requests presenting the same token are serialised by
     * PostgreSQL's row lock: exactly one sees a row still matching
     * `used_at IS NULL`, and the other's UPDATE matches zero rows.
     *
     * THE RETURN VALUE IS THE DECISION. Callers must branch on the affected-row
     * count and must never re-read the row to decide whether they won - that
     * would reintroduce the race this query exists to remove.
     *
     * `expires_at > :now` is evaluated here rather than in Java so that clock
     * skew between the application host and the database cannot extend a
     * token's life.
     *
     * clearAutomatically: a bulk UPDATE bypasses the persistence context, so any
     * EmailToken already loaded in this transaction would still report
     * `usedAt == null`. Clearing forces the follow-up read to see the truth.
     * flushAutomatically: pending inserts must reach the database before this
     * statement evaluates, or a token issued moments ago would be invisible.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailToken t
               SET t.usedAt = :now
             WHERE t.tokenHash = :tokenHash
               AND t.type = :type
               AND t.usedAt IS NULL
               AND t.invalidatedAt IS NULL
               AND t.expiresAt > :now
            """)
    int consume(@Param("tokenHash") String tokenHash,
                @Param("type") EmailTokenType type,
                @Param("now") Instant now);

    /**
     * Supersedes every live token of one type for one user.
     *
     * Called before issuing a replacement, which is what keeps
     * `uq_email_tokens_live` satisfiable - that partial unique index permits at
     * most one row per (user, type) with both terminal columns null.
     *
     * `invalidatedAt` rather than `usedAt`: the distinction is the whole reason
     * the table has two columns. "We replaced this" and "the user clicked this"
     * are different facts, and only the second is a click-through.
     *
     * NOT clearAutomatically. Clearing here would detach every managed entity in
     * the transaction, including the User the caller is about to attach to the
     * replacement token. Nothing in the issue path re-reads the rows this
     * statement touches, so flushing alone is sufficient and strictly safer.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE EmailToken t
               SET t.invalidatedAt = :now
             WHERE t.user.id = :userId
               AND t.type = :type
               AND t.usedAt IS NULL
               AND t.invalidatedAt IS NULL
            """)
    int invalidateLive(@Param("userId") UUID userId,
                       @Param("type") EmailTokenType type,
                       @Param("now") Instant now);

    /**
     * Classification, used ONLY after {@link #consume} returned zero.
     *
     * JOIN FETCH so the owning user is initialised inside the transaction: the
     * association is LAZY and the caller returns the User across the boundary,
     * where a bare proxy would throw LazyInitializationException.
     */
    @Query("""
            SELECT t FROM EmailToken t
            JOIN FETCH t.user
            WHERE t.tokenHash = :tokenHash
            """)
    Optional<EmailToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    /**
     * When the newest token of this type was created for this user, whatever
     * state it is now in. Backs the resend cooldown (Phase 4).
     *
     * ADDITIVE - it changes nothing about issue/consume/invalidate.
     *
     * DELIBERATELY IGNORES STATE. A superseded or consumed token still proves
     * an email was SENT, and the cooldown exists to protect the mailbox owner
     * from being mail-bombed, not to protect the token table. Filtering to live
     * rows would let an attacker reset the clock by consuming or superseding
     * the previous token.
     *
     * Durable by construction: it reads created_at from the database rather
     * than an in-memory counter, so a restart or a deploy cannot clear it. That
     * matters because the alternative would make the cooldown bypassable by
     * anyone who can trigger a redeploy - or simply by waiting for one.
     *
     * Served by ix_email_tokens_user_type_created, which V7 already created
     * with created_at DESC for exactly this query.
     */
    @Query("""
            SELECT MAX(t.createdAt) FROM EmailToken t
             WHERE t.user.id = :userId
               AND t.type = :type
            """)
    Optional<Instant> findLastCreatedAt(@Param("userId") UUID userId,
                                        @Param("type") EmailTokenType type);

    /**
     * Housekeeping. Deletes tokens that reached a terminal state before the
     * cutoff.
     *
     * Terminal rows are the audit trail for "who requested this, and did they
     * use it", so they are kept for a retention window rather than deleted on
     * consumption. Live rows are never touched regardless of age - an expired
     * but un-superseded token is still evidence, and it becomes terminal the
     * moment a replacement is issued.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM EmailToken t
             WHERE (t.usedAt IS NOT NULL AND t.usedAt < :cutoff)
                OR (t.invalidatedAt IS NOT NULL AND t.invalidatedAt < :cutoff)
            """)
    int deleteTerminalBefore(@Param("cutoff") Instant cutoff);
}
