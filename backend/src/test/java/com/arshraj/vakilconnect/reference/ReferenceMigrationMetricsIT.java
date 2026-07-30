package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.reference.metrics.ReferenceFallbackMetrics;
import com.arshraj.vakilconnect.reference.metrics.ReferenceMigrationMetrics;
import com.arshraj.vakilconnect.reference.reconciliation.ReferenceReconciliationService;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Migration observability (Phase 2G.5).
 *
 * The Phase 2H cleanup is gated on `cityFallbackReads` reaching zero, and until
 * this phase that number existed only inside the JVM. These tests are about the
 * gate being READABLE - that the meters exist under the documented names, that
 * they move when the read path moves, and that an unmigrated-but-idle system
 * reports a genuine zero rather than a gap.
 *
 * No servlet container runs under @SpringBootTest(MOCK), so /actuator/prometheus
 * is not reachable from here. The meters are asserted against the MeterRegistry
 * the endpoint would serialise, and the exposure configuration is asserted
 * separately - together those are the two halves of "exposed".
 */
@DisplayName("Migration observability")
class ReferenceMigrationMetricsIT extends AbstractIntegrationTest {

    private static final String READS = "vakilconnect.reference.city.reads";
    private static final String MISSING_PRIMARY = "vakilconnect.reference.lawyers.missing.primary.city";
    private static final String UNRESOLVED = "vakilconnect.reference.unresolved.cities";

    /** Not a curated city, so a lawyer here is permanently a fallback read. */
    private static final String UNRESOLVABLE_CITY = "Gotham";

    @Autowired private MeterRegistry meterRegistry;
    @Autowired private ReferenceFallbackMetrics fallbackMetrics;
    @Autowired private ReferenceReconciliationService reconciliationService;
    @Autowired private LawyerRepository lawyerRepository;

    @Value("${management.endpoints.web.exposure.include}")
    private String exposedEndpoints;

    @Value("${management.server.port}")
    private int managementPort;

    // ---------------------------------------------------------------- helpers

    private FunctionCounter reads(String source) {
        return meterRegistry.find(READS).tag("source", source).functionCounter();
    }

    private Gauge gauge(String name) {
        return meterRegistry.find(name).gauge();
    }

    private Lawyer seedLawyer(String city) throws Exception {
        String email = distinctEmail("metrics");

        assertEquals(201, register(lawyerRegistration(email, city)).getResponse().getStatus(),
                "seed registration must succeed for " + email);

        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        return lawyerRepository.findByUser(user).orElseThrow();
    }

    /** One public profile read, which maps exactly one city value. */
    private void readProfile(UUID lawyerId) throws Exception {
        mockMvc.perform(get("/api/lawyers/" + lawyerId)).andExpect(status().isOk());
    }

    // --------------------------------------------------------------- exposure

    @Nested
    @DisplayName("meters are exposed")
    class Exposure {

        @Test
        @DisplayName("both city read counters are registered and split by source")
        void readCountersAreRegistered() {
            assertNotNull(reads("reference"), "missing counter: " + READS + "{source=reference}");
            assertNotNull(reads("legacy"), "missing counter: " + READS + "{source=legacy}");
        }

        /**
         * One name with a `source` tag, not two names. The gate is a ratio, and
         * a ratio across two unrelated series is a join nobody wants to write at
         * 3am. Pinning this stops a later rename from quietly breaking the
         * dashboard query in the runbook.
         */
        @Test
        @DisplayName("the two counters share one name")
        void countersShareOneName() {
            assertEquals(2, meterRegistry.find(READS).functionCounters().size());
        }

        @Test
        @DisplayName("both reconciliation gauges are registered")
        void reconciliationGaugesAreRegistered() {
            assertNotNull(gauge(MISSING_PRIMARY), "missing gauge: " + MISSING_PRIMARY);
            assertNotNull(gauge(UNRESOLVED), "missing gauge: " + UNRESOLVED);
        }

        @Test
        @DisplayName("every meter carries a description")
        void metersAreDocumented() {
            assertNotNull(reads("legacy").getId().getDescription());
            assertNotNull(reads("reference").getId().getDescription());
            assertNotNull(gauge(MISSING_PRIMARY).getId().getDescription());
            assertNotNull(gauge(UNRESOLVED).getId().getDescription());
        }

        /**
         * The meters existing in the registry is half of "exposed"; the endpoint
         * being switched on is the other half, and it lives in configuration
         * where no compiler checks it.
         */
        @Test
        @DisplayName("prometheus is an exposed endpoint and actuator is off the application port")
        void endpointConfigurationIsCorrect() {
            assertTrue(exposedEndpoints.contains("prometheus"),
                    "prometheus must be exposed, got: " + exposedEndpoints);

            assertFalse(exposedEndpoints.contains("env"), "env must not be exposed");
            assertFalse(exposedEndpoints.contains("beans"), "beans must not be exposed");
            assertFalse(exposedEndpoints.contains("*"), "wildcard exposure is not permitted");

            assertTrue(managementPort > 0 && managementPort != 8080,
                    "actuator must not share the application port, got: " + managementPort);
        }
    }

    // ----------------------------------------------------------------- values

    @Nested
    @DisplayName("values track the read path")
    class Values {

        /**
         * The number Phase 2H is gated on. If this does not move, the gate is
         * measuring nothing and a premature "it has been zero for weeks" would
         * be indistinguishable from the truth.
         */
        @Test
        @DisplayName("a fallback read increments the legacy counter")
        void fallbackReadIncrementsLegacyCounter() throws Exception {
            Lawyer unlinked = seedLawyer(UNRESOLVABLE_CITY);

            double before = reads("legacy").count();
            readProfile(unlinked.getId());
            double after = reads("legacy").count();

            assertEquals(before + 1, after, 0.0001,
                    "one fallback-served city should be one legacy read");
        }

        @Test
        @DisplayName("a reference read increments the reference counter, not the legacy one")
        void referenceReadIncrementsReferenceCounter() throws Exception {
            Lawyer linked = seedLawyer("Nagpur");

            double referenceBefore = reads("reference").count();
            double legacyBefore = reads("legacy").count();

            readProfile(linked.getId());

            assertEquals(referenceBefore + 1, reads("reference").count(), 0.0001);
            assertEquals(legacyBefore, reads("legacy").count(), 0.0001,
                    "a linked lawyer must not be counted as a legacy read");
        }

        /**
         * The counters are a view onto ReferenceFallbackMetrics, not a second
         * tally kept alongside it. Two tallies drift; this asserts there is one.
         */
        @Test
        @DisplayName("the counters read through to the underlying adders")
        void countersMirrorTheUnderlyingMetrics() {
            assertEquals(fallbackMetrics.snapshot().cityFallbackReads(),
                    reads("legacy").count(), 0.0001);
            assertEquals(fallbackMetrics.snapshot().cityReferenceReads(),
                    reads("reference").count(), 0.0001);
        }

        /**
         * The backfill half of the gate. Fallback reads can sit at zero merely
         * because nobody browsed the unmigrated lawyers, so a second signal has
         * to say whether any remain.
         */
        @Test
        @DisplayName("an unresolvable city raises the unresolved-city gauge")
        void unresolvedGaugeReflectsUnmigratedData() throws Exception {
            double before = gauge(UNRESOLVED).value();
            double missingBefore = gauge(MISSING_PRIMARY).value();

            seedLawyer("Atlantis-" + UUID.randomUUID().toString().substring(0, 8));

            assertEquals(before + 1, gauge(UNRESOLVED).value(), 0.0001,
                    "a distinct unresolvable city name should appear in the gauge");
            assertEquals(missingBefore + 1, gauge(MISSING_PRIMARY).value(), 0.0001);
        }

        @Test
        @DisplayName("the gauges agree with the reconciliation report they summarise")
        void gaugesMatchTheReport() {
            assertEquals(reconciliationService.report().lawyersMissingPrimaryCity(),
                    gauge(MISSING_PRIMARY).value(), 0.0001);
            assertEquals(reconciliationService.report().unresolvedCityNames().size(),
                    gauge(UNRESOLVED).value(), 0.0001);
        }
    }

    // ------------------------------------------------------------------ zeros

    @Nested
    @DisplayName("zero is reported, not omitted")
    class Zeros {

        /**
         * THE MOST IMPORTANT CASE, and the easiest to get wrong.
         *
         * A migration that is finished and a migration that was never
         * instrumented both produce "no data" on a dashboard. Micrometer
         * registers a FunctionCounter immediately, so a system that has served
         * no fallback reads publishes 0.0 from startup rather than nothing at
         * all - which is what makes "it has been zero all week" a statement
         * about the application instead of a statement about the scraper.
         *
         * Asserted against a FRESH registry and a FRESH counter source, because
         * the shared application registry has been incremented by every test
         * that ran before this one.
         */
        @Test
        @DisplayName("an untouched system publishes 0.0, not an absent meter")
        void untouchedCountersPublishZero() {
            MeterRegistry fresh = new SimpleMeterRegistry();

            new ReferenceMigrationMetrics(
                    new ReferenceFallbackMetrics(), reconciliationService, Duration.ZERO)
                    .bindTo(fresh);

            FunctionCounter legacy = fresh.find(READS).tag("source", "legacy").functionCounter();
            FunctionCounter reference = fresh.find(READS).tag("source", "reference").functionCounter();

            assertNotNull(legacy, "the meter must exist before it has ever been incremented");
            assertNotNull(reference);
            assertEquals(0.0, legacy.count(), 0.0001);
            assertEquals(0.0, reference.count(), 0.0001);
        }

        /**
         * The gauges must report a real number too. NaN is reserved for "the
         * reconciliation query failed", and a scrape that cannot tell that apart
         * from a healthy zero would let Phase 2H proceed on a database outage.
         */
        @Test
        @DisplayName("the reconciliation gauges report a number, never NaN, while the database is up")
        void gaugesAreNeverNaNWhenHealthy() {
            assertFalse(Double.isNaN(gauge(MISSING_PRIMARY).value()),
                    "NaN means the reconciliation query failed");
            assertFalse(Double.isNaN(gauge(UNRESOLVED).value()));
        }
    }
}
