package com.arshraj.vakilconnect.reference.metrics;

import com.arshraj.vakilconnect.reference.reconciliation.ReconciliationReport;
import com.arshraj.vakilconnect.reference.reconciliation.ReferenceReconciliationService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.ToLongFunction;

/**
 * Publishes the Phase 2G migration signals as Micrometer meters (Phase 2G.5).
 *
 * WHY THIS EXISTS. The Phase 2H cleanup - dropping `lawyers.city` and the
 * fallback read path - is gated on `cityFallbackReads` reaching zero and
 * staying there. Until now those counters were process-local LongAdders with no
 * way out: readable from a debugger or a heap dump and nowhere else. A gate
 * nobody can observe is not a gate.
 *
 * ADAPTER, NOT REWRITE. ReferenceFallbackMetrics is untouched and remains the
 * thing the read path increments. This class only reads it. Two consequences
 * worth stating:
 *
 *   * the hot path stays a LongAdder increment - no Micrometer call, no tag
 *     lookup, no registry contention on a per-DTO-mapped operation
 *   * removing this class removes the observability and nothing else
 *
 * FunctionCounter, not Counter: the count already exists and is already
 * monotonic. FunctionCounter is Micrometer's idiom for surfacing a value you
 * are keeping anyway, rather than keeping it twice and hoping the two agree.
 *
 * WHY THE RECONCILIATION GAUGES ARE CACHED. ReconciliationReport is nine
 * aggregate queries over `lawyers` and `users`. A Prometheus scrape every 15s
 * would run them 5,760 times a day against tables on the request path, to move
 * a number that changes when someone edits a profile. The cache makes the
 * scrape interval and the query interval independent; the default TTL is far
 * longer than any sane scrape period, because this is a migration gauge read
 * over weeks, not a latency signal.
 */
@Component
public class ReferenceMigrationMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(ReferenceMigrationMetrics.class);

    /** Reported when the reconciliation query has never yet succeeded. */
    private static final double UNKNOWN = Double.NaN;

    private final ReferenceFallbackMetrics fallbackMetrics;
    private final ReferenceReconciliationService reconciliationService;
    private final Duration reconciliationTtl;

    private volatile ReconciliationReport cachedReport;
    private volatile Instant cachedAt = Instant.EPOCH;

    public ReferenceMigrationMetrics(
            ReferenceFallbackMetrics fallbackMetrics,
            ReferenceReconciliationService reconciliationService,
            @Value("${vakilconnect.migration.reconciliation-ttl:PT5M}") Duration reconciliationTtl) {

        this.fallbackMetrics = fallbackMetrics;
        this.reconciliationService = reconciliationService;
        this.reconciliationTtl = reconciliationTtl;
    }

    @Override
    public void bindTo(MeterRegistry registry) {

        /*
         * One counter name, split by where the value came from. Tagging rather
         * than naming them separately is what makes the gate expressible as a
         * single query - the ratio of legacy to total - instead of a join
         * between two unrelated series.
         */
        FunctionCounter.builder("vakilconnect.reference.city.reads", fallbackMetrics,
                        m -> m.snapshot().cityReferenceReads())
                .tag("source", "reference")
                .description("Lawyer city values served from the reference model")
                .register(registry);

        FunctionCounter.builder("vakilconnect.reference.city.reads", fallbackMetrics,
                        m -> m.snapshot().cityFallbackReads())
                .tag("source", "legacy")
                .description("Lawyer city values served from the legacy lawyers.city column")
                .register(registry);

        /*
         * The backfill side of the gate. Fallback reads can sit at zero simply
         * because nobody looked at the unmigrated lawyers; these say whether
         * unmigrated lawyers still exist at all.
         */
        Gauge.builder("vakilconnect.reference.lawyers.missing.primary.city", this,
                        self -> self.reportValue(ReconciliationReport::lawyersMissingPrimaryCity))
                .description("Lawyers with no primary_city_id")
                .register(registry);

        Gauge.builder("vakilconnect.reference.unresolved.cities", this,
                        self -> self.reportValue(r -> r.unresolvedCityNames().size()))
                .description("Distinct legacy city strings that resolve to no curated city")
                .register(registry);
    }

    /**
     * Reads one field out of the cached report, refreshing it if stale.
     *
     * Never throws. A gauge that propagates an exception during a scrape takes
     * the whole endpoint down with it, so a database that is briefly unavailable
     * would look like an application outage to whoever is watching. NaN is the
     * honest answer for "not known right now", and Prometheus records it as a
     * gap rather than as a zero - which matters, because a spurious zero here
     * reads as "migration complete".
     */
    private double reportValue(ToLongFunction<ReconciliationReport> field) {
        ReconciliationReport report = currentReport();
        return report == null ? UNKNOWN : field.applyAsLong(report);
    }

    private ReconciliationReport currentReport() {
        ReconciliationReport snapshot = cachedReport;

        if (snapshot != null && Duration.between(cachedAt, Instant.now()).compareTo(reconciliationTtl) < 0) {
            return snapshot;
        }

        try {
            snapshot = reconciliationService.report();
            cachedReport = snapshot;
            cachedAt = Instant.now();
            return snapshot;
        } catch (RuntimeException e) {
            // Keep serving the last known values rather than a gap, but do not
            // refresh the timestamp: the next scrape retries.
            log.warn("Reference reconciliation query failed; serving last known values", e);
            return cachedReport;
        }
    }
}
