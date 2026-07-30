package com.arshraj.vakilconnect.reference.metrics;

import com.arshraj.vakilconnect.reference.reconciliation.ReconciliationReport;
import com.arshraj.vakilconnect.reference.reconciliation.ReferenceReconciliationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconciliation freshness (Phase 2G.8).
 *
 * A UNIT test, not an integration one, and deliberately.
 *
 * Staleness is a statement about elapsed time. Asserting it against the running
 * application would mean either sleeping - slow, and flaky the first time CI is
 * loaded - or waiting on the real clock to cross a TTL boundary. With the clock
 * injected, the same behaviour is provable in microseconds and the assertions
 * are exact rather than "roughly".
 *
 * What this leaves to ReferenceMigrationMetricsIT: that the gauge is registered
 * in the application's own registry and reads sanely against a live database.
 */
@DisplayName("Reconciliation freshness")
class ReferenceMigrationFreshnessTest {

    private static final String AGE = "vakilconnect.reference.reconciliation.age.seconds";
    private static final String UNRESOLVED = "vakilconnect.reference.unresolved.cities";

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        private Instant now;

        private TestClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public Instant instant() {
            return now;
        }

        @Override public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    /**
     * Reconciliation stub.
     *
     * Subclassed rather than mocked - this codebase uses no mocking framework,
     * and `report()` is the only method, so an override is a smaller precedent
     * than a new testing style. Null repositories are safe precisely because
     * the override never reaches them.
     */
    private static final class StubReconciliation extends ReferenceReconciliationService {
        private boolean failing;
        private int calls;

        private StubReconciliation() {
            super(null, null);
        }

        @Override public ReconciliationReport report() {
            calls++;
            if (failing) {
                throw new IllegalStateException("simulated database failure");
            }
            return new ReconciliationReport(10, 0, List.of(), 0, 10, 10, 10, 10);
        }
    }

    private record Fixture(ReferenceMigrationMetrics metrics,
                           MeterRegistry registry,
                           TestClock clock,
                           StubReconciliation reconciliation) {

        double age() {
            return registry.find(AGE).gauge().value();
        }

        double unresolved() {
            return registry.find(UNRESOLVED).gauge().value();
        }
    }

    private Fixture fixture(Duration ttl) {
        TestClock clock = new TestClock(T0);
        StubReconciliation reconciliation = new StubReconciliation();
        MeterRegistry registry = new SimpleMeterRegistry();

        ReferenceMigrationMetrics metrics = new ReferenceMigrationMetrics(
                new ReferenceFallbackMetrics(), reconciliation, ttl, clock);
        metrics.bindTo(registry);

        return new Fixture(metrics, registry, clock, reconciliation);
    }

    // ------------------------------------------------------------ first start

    @Nested
    @DisplayName("before any refresh has succeeded")
    class FirstStart {

        /**
         * The gauge is readable from the moment it is bound. An absent series
         * and a zero series mean different things to a dashboard, and "we have
         * not scraped yet" must not look like "reconciliation is fresh".
         */
        @Test
        @DisplayName("the gauge exists before anything has been scraped")
        void gaugeExistsAtStartup() {
            assertNotNull(fixture(Duration.ofMinutes(5)).registry.find(AGE).gauge());
        }

        /**
         * Age measures from application start, NOT from the epoch and NOT as a
         * sentinel.
         *
         * NaN would have matched the value gauges, but `NaN > 900` is false in
         * PromQL - so the state that most needs paging, reconciliation never
         * having worked since boot, would page nobody. Elapsed-since-start is
         * true, rises on its own, and trips the ordinary threshold alert.
         */
        @Test
        @DisplayName("age is measured from application start, not from the epoch")
        void ageBeforeFirstSuccessCountsFromStartup() {
            Fixture f = fixture(Duration.ofMinutes(5));
            f.reconciliation.failing = true;

            f.clock.advance(Duration.ofSeconds(90));

            assertEquals(90.0, f.age(), 0.001,
                    "must be seconds since start, not ~1.7e9 seconds since 1970");
        }

        /**
         * The pair that tells an operator which failure they are looking at:
         * never-succeeded shows a rising age AND NaN values, whereas a merely
         * stale cache shows a rising age and real numbers.
         */
        @Test
        @DisplayName("a never-successful reconciliation reports NaN values alongside a rising age")
        void neverSuccessfulReportsNaNValues() {
            Fixture f = fixture(Duration.ofMinutes(5));
            f.reconciliation.failing = true;

            f.clock.advance(Duration.ofSeconds(30));

            assertTrue(Double.isNaN(f.unresolved()), "no data yet means NaN, not zero");
            assertEquals(30.0, f.age(), 0.001);
        }
    }

    // ----------------------------------------------------------------- fresh

    @Nested
    @DisplayName("after a successful refresh")
    class Fresh {

        @Test
        @DisplayName("age is zero immediately after a refresh")
        void ageIsZeroWhenJustRefreshed() {
            Fixture f = fixture(Duration.ofMinutes(5));

            f.unresolved();   // forces the first refresh

            assertEquals(0.0, f.age(), 0.001);
        }

        @Test
        @DisplayName("age tracks elapsed time while the cache is still within its TTL")
        void ageGrowsWithinTtl() {
            Fixture f = fixture(Duration.ofMinutes(5));

            f.unresolved();
            f.clock.advance(Duration.ofSeconds(120));

            assertEquals(120.0, f.age(), 0.001);
            assertEquals(1, f.reconciliation.calls,
                    "still inside the TTL, so no second query should have run");
        }

        @Test
        @DisplayName("age resets when the TTL expires and the cache refreshes")
        void ageResetsOnRefresh() {
            Fixture f = fixture(Duration.ofMinutes(5));

            f.unresolved();
            f.clock.advance(Duration.ofMinutes(6));
            f.unresolved();   // TTL passed, so this re-queries

            assertEquals(0.0, f.age(), 0.001);
            assertEquals(2, f.reconciliation.calls);
        }
    }

    // ----------------------------------------------------------------- stale

    @Nested
    @DisplayName("when refreshes stop succeeding")
    class Stale {

        /**
         * THE CASE THIS PHASE EXISTS FOR.
         *
         * On failure the cache is served anyway - a brief blip should not blank
         * the dashboard - so the values keep looking healthy. A stale
         * `unresolved_cities = 0` is exactly the reading that would wrongly open
         * the Phase 2H gate, and until now the only trace was a log line.
         */
        @Test
        @DisplayName("values stay readable but the age keeps climbing")
        void staleCacheKeepsServingWhileAgeClimbs() {
            Fixture f = fixture(Duration.ofMinutes(5));

            f.unresolved();                       // good data cached at T0
            f.reconciliation.failing = true;
            f.clock.advance(Duration.ofHours(3));

            assertEquals(0.0, f.unresolved(), 0.001,
                    "the last known value is still served");
            assertEquals(10800.0, f.age(), 0.001,
                    "three hours stale, and the gauge says so");
        }

        /**
         * Age measures time since good DATA, not since the last attempt. A
         * failing refresh must not reset the clock, or a database that is down
         * but reachable enough to error would report itself perpetually fresh.
         */
        @Test
        @DisplayName("a failed refresh does not reset the age")
        void failedRefreshDoesNotResetAge() {
            Fixture f = fixture(Duration.ofSeconds(1));

            f.unresolved();
            f.reconciliation.failing = true;

            f.clock.advance(Duration.ofSeconds(10));
            f.unresolved();   // attempts, fails
            f.clock.advance(Duration.ofSeconds(10));
            f.unresolved();   // attempts, fails

            assertEquals(20.0, f.age(), 0.001);
            assertTrue(f.reconciliation.calls >= 3, "the failing refresh should keep retrying");
        }

        @Test
        @DisplayName("recovery brings the age back to zero")
        void recoveryResetsAge() {
            Fixture f = fixture(Duration.ofSeconds(1));

            f.unresolved();
            f.reconciliation.failing = true;
            f.clock.advance(Duration.ofHours(1));
            f.unresolved();

            assertEquals(3600.0, f.age(), 0.001);

            f.reconciliation.failing = false;
            f.unresolved();

            assertEquals(0.0, f.age(), 0.001);
        }
    }
}
