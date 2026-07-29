package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.reconciliation.ReconciliationReport;
import com.arshraj.vakilconnect.reference.reconciliation.ReferenceReconciliationService;
import com.arshraj.vakilconnect.reference.repository.CityRepository;
import com.arshraj.vakilconnect.reference.repository.StateRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reference backfill and reconciliation (Phase 2F).
 *
 * The migration itself is under test, not a copy of it: each test executes the
 * real `V6__backfill_reference_links.sql` from the classpath. A test that
 * re-implemented the SQL would pass while the shipped file was broken.
 *
 * Re-running V6 against an already-migrated database is exactly what these
 * tests do, which is also how idempotency is verified - Flyway has already
 * applied it once during container startup.
 *
 * ISOLATION. Test classes share one database and other classes create lawyers
 * continuously, so nothing here asserts an absolute count. Every assertion is
 * about a specific row this test created, or about a delta it caused.
 */
@DisplayName("Reference backfill")
class ReferenceBackfillIT extends AbstractIntegrationTest {

    private static final String MIGRATION = "db/migration/V6__backfill_reference_links.sql";

    @Autowired private LawyerRepository lawyerRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private StateRepository stateRepository;
    @Autowired private ReferenceReconciliationService reconciliationService;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** Runs the shipped migration file exactly as Flyway would. */
    private void runBackfill() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
        }
    }

    /**
     * Puts a lawyer back into the pre-migration state.
     *
     * The Phase 2E dual-write links every new lawyer on creation, so a row that
     * looks like legacy data has to be manufactured: clear the FK and the join
     * rows, and set whatever legacy string the case under test needs.
     */
    private void makeUnmigrated(UUID lawyerId, String legacyCity) {
        jdbcTemplate.update(
                "UPDATE lawyers SET primary_city_id = NULL, city = ? WHERE id = ?",
                legacyCity, lawyerId);
        jdbcTemplate.update(
                "DELETE FROM lawyer_practice_cities WHERE lawyer_id = ?", lawyerId);
    }

    private UUID seedLawyer() throws Exception {
        String email = uniqueEmail("backfill");
        registerAndLoginLawyer(email);
        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        return lawyerRepository.findByUser(user).orElseThrow().getId();
    }

    private Lawyer withPrimaryCity(UUID lawyerId) {
        return lawyerRepository.findByIdWithPrimaryCity(lawyerId).orElseThrow();
    }

    private Set<String> practiceCityNames(UUID lawyerId) {
        return lawyerRepository.findByIdWithPracticeCities(lawyerId).orElseThrow()
                .getPracticeCities().stream()
                .map(City::getName)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------- backfilling

    @Nested
    @DisplayName("backfill")
    class Backfill {

        @Test
        @DisplayName("an exact city name is linked")
        void exactNameIsLinked() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Pune");

            assertNull(withPrimaryCity(lawyerId).getPrimaryCity(), "precondition");

            runBackfill();

            assertEquals("Pune", withPrimaryCity(lawyerId).getPrimaryCity().getName());
        }

        /**
         * Normalisation in SQL must agree with TextNormalizer for the cases that
         * occur: casing and stray whitespace.
         */
        @Test
        @DisplayName("casing and padding do not prevent a match")
        void normalisationHandlesCasingAndPadding() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "   pUNe  ");

            runBackfill();

            assertEquals("Pune", withPrimaryCity(lawyerId).getPrimaryCity().getName());
        }

        /**
         * The alias pass is what maps the historical names still in daily use.
         * Without it a large share of legacy free text would stay unmapped.
         */
        @Test
        @DisplayName("a historical name is linked through the alias table")
        void aliasIsLinked() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Bombay");

            runBackfill();

            Lawyer lawyer = withPrimaryCity(lawyerId);
            assertEquals("Mumbai", lawyer.getPrimaryCity().getName());
            // The legacy column is never rewritten by the migration.
            assertEquals("Bombay", lawyer.getCity());
        }

        @Test
        @DisplayName("the primary city is added to the practice set")
        void primaryCityJoinsThePracticeSet() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Kochi");

            assertTrue(practiceCityNames(lawyerId).isEmpty(), "precondition");

            runBackfill();

            assertEquals(Set.of("Kochi"), practiceCityNames(lawyerId));
        }

        /**
         * Never guess. An unrecognisable value stays NULL and shows up in the
         * report, rather than being attached to the nearest-looking city.
         */
        @Test
        @DisplayName("an unresolvable name is left NULL")
        void unresolvableNameStaysNull() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Atlantis");

            runBackfill();

            Lawyer lawyer = withPrimaryCity(lawyerId);
            assertNull(lawyer.getPrimaryCity(), "must not guess a city");
            assertEquals("Atlantis", lawyer.getCity());
            assertTrue(practiceCityNames(lawyerId).isEmpty());
        }
    }

    // ---------------------------------------------------------- non-destructive

    @Nested
    @DisplayName("existing links")
    class ExistingLinks {

        /**
         * The migration guards every UPDATE with `primary_city_id IS NULL`, so a
         * link written by the dual-write - or by an operator correcting one by
         * hand - survives untouched even when the legacy string disagrees.
         */
        @Test
        @DisplayName("a valid existing link is never overwritten")
        void existingLinkIsNotOverwritten() throws Exception {
            UUID lawyerId = seedLawyer();

            // Deliberately contradictory: link says Delhi, legacy string says Pune.
            City delhi = cityRepository.findByStateIdAndNameNormalized(
                    stateRepository.findByCountryIso2IgnoreCaseAndCodeIgnoreCase("IN", "DL")
                            .orElseThrow().getId(), "delhi").orElseThrow();

            jdbcTemplate.update(
                    "UPDATE lawyers SET primary_city_id = ?, city = ? WHERE id = ?",
                    delhi.getId(), "Pune", lawyerId);

            runBackfill();

            assertEquals("Delhi", withPrimaryCity(lawyerId).getPrimaryCity().getName(),
                    "the migration must not re-resolve an already-linked row");
        }

        @Test
        @DisplayName("the legacy column is never modified")
        void legacyColumnIsUntouched() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "  bangalore ");

            runBackfill();

            assertEquals("  bangalore ", lawyerRepository.findById(lawyerId)
                    .orElseThrow().getCity(), "legacy text is preserved verbatim");
            assertEquals("Bengaluru",
                    withPrimaryCity(lawyerId).getPrimaryCity().getName());
        }
    }

    // -------------------------------------------------------------- idempotency

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        /**
         * Flyway already applied V6 once at startup; these runs are the second
         * and third. Running it repeatedly must converge, not accumulate.
         */
        @Test
        @DisplayName("running the migration twice produces the same result")
        void secondRunChangesNothing() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Jaipur");

            runBackfill();
            UUID afterFirst = withPrimaryCity(lawyerId).getPrimaryCity().getId();
            Set<String> citiesAfterFirst = practiceCityNames(lawyerId);

            runBackfill();

            assertEquals(afterFirst, withPrimaryCity(lawyerId).getPrimaryCity().getId());
            assertEquals(citiesAfterFirst, practiceCityNames(lawyerId));
        }

        /**
         * The join table has no unique constraint violation to hide behind if the
         * ON CONFLICT were wrong - a duplicate would fail the insert outright -
         * but a second row for the same pair would also be silently wrong if the
         * key were ever relaxed. Counted explicitly.
         */
        @Test
        @DisplayName("re-running does not duplicate practice-city rows")
        void noDuplicateJoinRows() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Chennai");

            runBackfill();
            runBackfill();

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM lawyer_practice_cities WHERE lawyer_id = ?",
                    Integer.class, lawyerId);

            assertEquals(1, rows);
        }
    }

    // ------------------------------------------------------------ reconciliation

    @Nested
    @DisplayName("reconciliation report")
    class Reconciliation {

        @Test
        @DisplayName("an unresolved lawyer is counted and its city name listed")
        void unresolvedLawyerIsReported() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Shangri-La");
            runBackfill();

            ReconciliationReport report = reconciliationService.report();

            assertTrue(report.lawyersMissingPrimaryCity() >= 1);
            assertTrue(report.unresolvedCityNames().contains("Shangri-La"),
                    "the actionable name should be listed: "
                            + report.unresolvedCityNames());
            assertFalse(report.cityBackfillComplete());
        }

        @Test
        @DisplayName("a resolved lawyer is not reported as missing")
        void resolvedLawyerIsNotReported() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Kolkata");
            runBackfill();

            ReconciliationReport report = reconciliationService.report();

            assertFalse(report.unresolvedCityNames().contains("Kolkata"));
            assertNotNull(withPrimaryCity(lawyerId).getPrimaryCity());
        }

        /**
         * Three of the eight figures are expected to stay at their maximum:
         * `users` has never held a city or language and `lawyers` has never held
         * a language, so V4 added those columns with nothing to migrate from.
         *
         * Asserting it keeps the gap visible - if a later phase starts collecting
         * this data, this test fails and forces the report to be revisited rather
         * than left quietly wrong.
         */
        @Test
        @DisplayName("targets with no legacy source are reported as fully unmapped")
        void targetsWithoutASourceAreReportedUnmapped() {
            ReconciliationReport report = reconciliationService.report();

            assertEquals(report.totalUsers(), report.usersMissingCity(),
                    "users have never had a city column to migrate from");
            assertEquals(report.totalUsers(), report.usersMissingPreferredLanguage(),
                    "users have never had a language column to migrate from");
            assertEquals(report.totalLawyers(), report.lawyersMissingLanguages(),
                    "lawyers have never had a language column to migrate from");
        }

        @Test
        @DisplayName("the report is read-only")
        void reportDoesNotMutate() throws Exception {
            UUID lawyerId = seedLawyer();
            makeUnmigrated(lawyerId, "Atlantis");

            reconciliationService.report();
            reconciliationService.report();

            // Still unmigrated - reporting must not backfill as a side effect.
            assertNull(withPrimaryCity(lawyerId).getPrimaryCity());
            assertTrue(practiceCityNames(lawyerId).isEmpty());
        }

        @Test
        @DisplayName("the summary line reflects the counts")
        void summaryReflectsCounts() {
            ReconciliationReport report = reconciliationService.report();

            String summary = report.summary();
            assertTrue(summary.contains("lawyers=" + report.totalLawyers()), summary);
            assertTrue(
                    summary.contains("missingPrimaryCity=" + report.lawyersMissingPrimaryCity()),
                    summary);
        }
    }

    // ------------------------------------------------------------ documentation

    /**
     * Guards the claim made in the V6 header: only two of the five reference
     * targets have a legacy source, so the other three are deliberately not
     * written. If someone later adds a source column and forgets the migration,
     * this is the test that notices.
     */
    @Test
    @DisplayName("the migration writes only the targets that have a legacy source")
    void migrationScopeIsDocumented() throws Exception {
        String sql = new String(new ClassPathResource(MIGRATION)
                .getInputStream().readAllBytes());

        List<String> writes = sql.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("UPDATE ") || line.startsWith("INSERT INTO "))
                .toList();

        assertEquals(3, writes.size(), "unexpected write statements: " + writes);
        assertTrue(writes.stream().filter(w -> w.startsWith("UPDATE lawyers")).count() == 2);
        assertTrue(writes.stream()
                .anyMatch(w -> w.startsWith("INSERT INTO lawyer_practice_cities")));

        assertFalse(sql.contains("UPDATE users"), "users has no legacy source to migrate");
        assertFalse(sql.contains("INSERT INTO lawyer_languages"),
                "lawyers have no legacy language source to migrate");
    }
}
