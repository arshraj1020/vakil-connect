package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7 — schema, constraints and configuration binding.
 *
 * The whole application context starting at all is already a meaningful
 * assertion: `ddl-auto: validate` runs against the real migrated schema, so a
 * mismatch between the new User.credentialsChangedAt mapping and the
 * `timestamptz` column would fail every test in the suite, not just this class.
 * That is the check for "Hibernate validate succeeds".
 *
 * WHAT THIS CLASS CANNOT TEST. Step 2 of V7 grandfathers pre-existing accounts
 * to verified. The container is created empty and Flyway runs before any test
 * data exists, so the UPDATE matches zero rows here and there is nothing to
 * assert. Its effect is only observable against a database that already held
 * users, which is a manual check against a restored snapshot - it is on the
 * Phase 1 acceptance checklist for that reason, not omitted by oversight.
 *
 * No @Transactional: several tests below assert that a constraint REJECTS a
 * write, and a rolled-back-but-open transaction would poison later statements
 * in the same test. Each JdbcTemplate call commits on its own.
 */
@DisplayName("V7 — email verification schema")
class V7MigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdentityProperties identityProperties;

    /**
     * The container is shared by every test class in the JVM, so fixtures are
     * removed rather than left to accumulate. Tokens go with the user through
     * the cascade this class also asserts.
     */
    @AfterEach
    void removeFixtures() {
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'v7\\_%'");
    }

    /**
     * A committed user row to hang tokens off. Unique per call.
     *
     * credentials_changed_at is deliberately omitted: the column is NOT NULL, so
     * this insert only succeeds because V7's DEFAULT now() supplies it.
     */
    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, created_at, updated_at, full_name, email,
                                   password_hash, is_email_verified, role, active)
                VALUES (?, now(), now(), 'V7 Fixture', ?, 'x', false, 'CLIENT', true)
                """,
                id, "v7_" + UUID.randomUUID() + "@test.com");
        return id;
    }

    /**
     * Nullable timestamptz parameters carry an explicit JDBC type.
     *
     * A bare null reaches the driver as Types.NULL, and PostgreSQL then has to
     * infer the parameter type. It usually can from the target column, but
     * "could not determine data type of parameter" is a real failure mode and
     * not one worth leaving to inference in a test whose whole job is to prove
     * the constraints behave.
     */
    private static SqlParameterValue timestamptz(Instant value) {
        return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE,
                value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    private void insertToken(UUID userId, String type, String hash,
                             Instant usedAt, Instant invalidatedAt) {
        jdbcTemplate.update("""
                INSERT INTO email_tokens (id, user_id, type, token_hash, expires_at,
                                          used_at, invalidated_at)
                VALUES (?, ?, ?, ?, now() + interval '1 hour', ?, ?)
                """,
                UUID.randomUUID(), userId, type, hash,
                timestamptz(usedAt), timestamptz(invalidatedAt));
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    // ------------------------------------------------------------------ shape

    @Nested
    @DisplayName("schema shape")
    class Shape {

        @Test
        @DisplayName("V7 is recorded as applied in the Flyway history")
        void migrationApplied() {
            Boolean success = jdbcTemplate.queryForObject(
                    "SELECT success FROM flyway_schema_history WHERE version = '7'",
                    Boolean.class);

            assertEquals(Boolean.TRUE, success, "V7 should be recorded successful");
        }

        @Test
        @DisplayName("users.credentials_changed_at is a NOT NULL timestamptz")
        void credentialsChangedAtColumn() {
            assertEquals("timestamp with time zone",
                    columnType("users", "credentials_changed_at"),
                    "must be zone-aware: this value is compared against now() to "
                            + "decide whether a security token is still valid");

            String nullable = jdbcTemplate.queryForObject("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'users' AND column_name = 'credentials_changed_at'
                    """, String.class);

            assertEquals("NO", nullable);
        }

        @Test
        @DisplayName("token_hash is varchar, not the blank-padded char(64)")
        void tokenHashIsVarchar() {
            // char(64) would be reported as "character" and would fail
            // ddl-auto: validate against Hibernate's varchar mapping for String.
            assertEquals("character varying", columnType("email_tokens", "token_hash"));
        }

        @Test
        @DisplayName("requested_user_agent is unbounded text")
        void userAgentIsText() {
            // Real UA strings routinely exceed 255 characters. A bounded column
            // would abort the enclosing registration transaction.
            assertEquals("text", columnType("email_tokens", "requested_user_agent"));
        }

        @Test
        @DisplayName("every email_tokens timestamp is zone-aware")
        void timestampsAreZoneAware() {
            for (String column : new String[]{
                    "expires_at", "used_at", "invalidated_at", "created_at"}) {
                assertEquals("timestamp with time zone",
                        columnType("email_tokens", column),
                        column + " must be timestamptz");
            }
        }

        @Test
        @DisplayName("email_tokens has no updated_at, unlike every other table")
        void noUpdatedAt() {
            // Deliberate: a token is immutable once issued except for reaching a
            // terminal state, and used_at / invalidated_at record that with more
            // information than a generic updated_at would.
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_name = 'email_tokens' AND column_name = 'updated_at'
                    """, Integer.class);

            assertEquals(0, count);
        }

        @Test
        @DisplayName("uq_email_tokens_live is unique AND partial")
        void liveTokenIndexIsPartial() {
            String definition = jdbcTemplate.queryForObject("""
                    SELECT indexdef FROM pg_indexes
                    WHERE tablename = 'email_tokens' AND indexname = 'uq_email_tokens_live'
                    """, String.class);

            assertNotNull(definition, "uq_email_tokens_live should exist");
            assertTrue(definition.contains("UNIQUE"), definition);
            // Partial, so consumed and superseded rows accumulate freely as an
            // audit trail while only live rows are constrained.
            assertTrue(definition.contains("WHERE"), definition);
        }

        @Test
        @DisplayName("all four indexes exist")
        void indexesExist() {
            for (String index : new String[]{
                    "uq_email_tokens_hash",
                    "uq_email_tokens_live",
                    "ix_email_tokens_user_type_created",
                    "ix_email_tokens_expires"}) {

                Integer count = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM pg_indexes
                        WHERE tablename = 'email_tokens' AND indexname = ?
                        """, Integer.class, index);

                assertEquals(1, count, index + " should exist");
            }
        }
    }

    // ------------------------------------------------------------ constraints

    @Nested
    @DisplayName("constraints")
    class Constraints {

        @Test
        @DisplayName("rejects a type outside the enum")
        void rejectsUnknownType() {
            UUID user = insertUser();

            assertThrows(DataIntegrityViolationException.class,
                    () -> insertToken(user, "MAGIC_LINK", UUID.randomUUID().toString(), null, null),
                    "ck_email_tokens_type should mirror the Java enum");
        }

        @Test
        @DisplayName("rejects a token that is both used and invalidated")
        void rejectsBothTerminalStates() {
            UUID user = insertUser();
            Instant now = Instant.now();

            assertThrows(DataIntegrityViolationException.class,
                    () -> insertToken(user, "VERIFY_EMAIL",
                            UUID.randomUUID().toString(), now, now),
                    "the two terminal states are mutually exclusive");
        }

        @Test
        @DisplayName("allows either terminal state on its own")
        void allowsEitherTerminalState() {
            UUID user = insertUser();
            Instant now = Instant.now();

            insertToken(user, "VERIFY_EMAIL", UUID.randomUUID().toString(), now, null);
            insertToken(user, "VERIFY_EMAIL", UUID.randomUUID().toString(), null, now);
            // Two terminal rows for one (user, type) coexist: the live index is
            // partial, so it does not constrain them.
        }

        @Test
        @DisplayName("rejects a second LIVE token for the same user and type")
        void enforcesOneLiveTokenPerUserAndType() {
            UUID user = insertUser();

            insertToken(user, "VERIFY_EMAIL", UUID.randomUUID().toString(), null, null);

            assertThrows(DataIntegrityViolationException.class,
                    () -> insertToken(user, "VERIFY_EMAIL",
                            UUID.randomUUID().toString(), null, null),
                    "uq_email_tokens_live makes 'invalidate before reissue' a "
                            + "database rule, so two concurrent resends cannot both win");
        }

        @Test
        @DisplayName("allows one live token of each type for the same user")
        void allowsOneLiveTokenPerType() {
            UUID user = insertUser();

            insertToken(user, "VERIFY_EMAIL", UUID.randomUUID().toString(), null, null);
            insertToken(user, "RESET_PASSWORD", UUID.randomUUID().toString(), null, null);
            // A user mid-verification must still be able to reset their password.
        }

        @Test
        @DisplayName("rejects a duplicate token hash")
        void rejectsDuplicateHash() {
            UUID first = insertUser();
            UUID second = insertUser();
            String sharedHash = UUID.randomUUID().toString();

            insertToken(first, "VERIFY_EMAIL", sharedHash, null, null);

            assertThrows(DataIntegrityViolationException.class,
                    () -> insertToken(second, "VERIFY_EMAIL", sharedHash, null, null),
                    "a collision at 256 bits of entropy could only be a defect");
        }

        @Test
        @DisplayName("deleting a user cascades to their tokens")
        void cascadesOnUserDelete() {
            UUID user = insertUser();
            insertToken(user, "VERIFY_EMAIL", UUID.randomUUID().toString(), null, null);

            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);

            Integer remaining = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM email_tokens WHERE user_id = ?",
                    Integer.class, user);

            assertEquals(0, remaining, "a token is meaningless without its user");
        }
    }

    // --------------------------------------------------------- configuration

    @Nested
    @DisplayName("configuration binding")
    class Configuration {

        @Test
        @DisplayName("every identity property binds from application.yaml")
        void propertiesBind() {
            // @Validated + @NotNull means a missing key is already a startup
            // failure. This asserts the VALUES, so a typo in a key name - which
            // binds to null and would only surface later - is caught here.
            assertEquals(Duration.ofHours(24), identityProperties.verificationTokenTtl());
            assertEquals(Duration.ofMinutes(30), identityProperties.resetTokenTtl());
            assertEquals(Duration.ofSeconds(60), identityProperties.resendCooldown());
            assertEquals(Duration.ofDays(7), identityProperties.takeoverThreshold());
            assertEquals(Duration.ofDays(30), identityProperties.tokenPurgeRetention());
            assertEquals(Duration.ofDays(30), identityProperties.unverifiedPurgeAfter());
            assertNotNull(identityProperties.publicBaseUrl());
        }

        @Test
        @DisplayName("the login gate ships disabled")
        void gateIsOffByDefault() {
            // Phase 1 must be behaviourally invisible. This also guards the
            // deploy default: email delivery has to be proven in production
            // before anything is gated on it.
            assertFalse(identityProperties.verificationEnforced(),
                    "verification must not be enforced by default");
        }

        @Test
        @DisplayName("unverified accounts outlive the takeover window")
        void purgeOutlivesTakeover() {
            // If an address were purged before takeover became possible, the
            // takeover path would be unreachable. Documented as an invariant
            // rather than enforced in the binder, so it is asserted here.
            assertTrue(identityProperties.unverifiedPurgeAfter()
                            .compareTo(identityProperties.takeoverThreshold()) >= 0,
                    "UNVERIFIED_PURGE_AFTER must be >= TAKEOVER_THRESHOLD");
        }
    }
}
