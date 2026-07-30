package com.arshraj.vakilconnect.reference.metrics;

import com.arshraj.vakilconnect.reference.reconciliation.ReconciliationReport;
import com.arshraj.vakilconnect.reference.reconciliation.ReferenceReconciliationService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
    private final Clock clock;

    /** When this instance was created - the age baseline before any refresh succeeds. */
    private final Instant startedAt;

    private volatile ReconciliationReport cachedReport;
    private volatile Instant cachedAt = Instant.EPOCH;

    @Autowired
    public ReferenceMigrationMetrics(
            ReferenceFallbackMetrics fallbackMetrics,
            ReferenceReconciliationService reconciliationService,
            @Value("${vakilconnect.migration.reconciliation-ttl:PT5M}") Duration reconciliationTtl) {

        this(fallbackMetrics, reconciliationService, reconciliationTtl, Clock.systemUTC());
    }

    /**
     * Clock-injecting constructor, for tests.
     *
     * Staleness is a statement about elapsed time, and a test that asserts it by
     * sleeping is both slow and flaky. Package-private: nothing in the
     * application should be choosing a clock.
     */
    ReferenceMigrationMetrics(
            ReferenceFallbackMetrics fallbackMetrics,
            ReferenceReconciliationService reconciliationService,
            Duration reconciliationTtl,
            Clock clock) {

        this.fallbackMetrics = fallbackMetrics;
        this.reconciliationService = reconciliationService;
        this.reconciliationTtl = reconciliationTtl;
        this.clock = clock;
        this.startedAt = clock.instant();
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

        /*
         * THE SEARCH BRANCH, which the two meters above cannot see.
         *
         * The DTO path falls back on `primaryCity == null`; the search predicate
         * falls back on `practiceCities IS EMPTY`. Those agree on every row that
         * honours the Option C invariant, and disagree only on a row that
         * violates it - primary set, practice set empty.
         *
         * Such a row is invisible to everything else here: search serves it from
         * the legacy branch with nothing counting that, its DTO reads
         * `primaryCity` and so increments source="reference", and
         * lawyers.missing.primary.city reads zero for it. Without this gauge the
         * Phase 2H gate can go green while the legacy search branch is still
         * live for part of the table.
         *
         * Same cached report as the gauges above - no additional query.
         */
        Gauge.builder("vakilconnect.reference.lawyers.missing.practice.cities", this,
                        self -> self.reportValue(ReconciliationReport::lawyersMissingPracticeCities))
                .description("Lawyers with no rows in lawyer_practice_cities, "
                        + "which the search predicate serves from the legacy branch")
                .register(registry);

        /*
         * HOW OLD THE THREE GAUGES ABOVE ARE.
         *
         * They serve a cached report. On a query failure the cache is served
         * ANYWAY - deliberately, so a brief database blip does not blank the
         * dashboard - and the only other trace is a log line. That makes a stale
         * reading indistinguishable from a live one, and a stale
         * `unresolved_cities = 0` is precisely the reading that would wrongly
         * open the Phase 2H gate.
         *
         * Reuses `cachedAt`, which is written only after a SUCCESSFUL refresh -
         * so this measures time since good data, not time since the last
         * attempt. No second cache, no scheduler.
         *
         * NO baseUnit IS SET even though the values are seconds: Micrometer's
         * Prometheus naming convention appends the base unit to the name, and
         * the name already ends in `.seconds`. Setting both risks
         * `..._seconds_seconds`.
         */
        Gauge.builder("vakilconnect.reference.reconciliation.age.seconds", this,
                        ReferenceMigrationMetrics::reconciliationAgeSeconds)
                .description("Seconds since the reconciliation report was last refreshed "
                        + "successfully; measured from application start if it never has")
                .register(registry);
    }

    /**
     * Age of the cached report in seconds.
     *
     * BEFORE THE FIRST SUCCESS this measures from application start rather than
     * reporting a sentinel, and that is the deliberate choice here.
     *
     * NaN would have been consistent with the value gauges, but it breaks the
     * alert: `NaN > 900` is false in PromQL, so the one state that most needs
     * paging - reconciliation has never worked since this process booted -
     * would page nobody. A magic negative has the same defect. Elapsed-since-
     * start is a true statement ("there has been no good data for this long"),
     * it rises monotonically, and it makes the obvious threshold alert fire.
     *
     * Telling the two states apart is still possible, and the runbook says how:
     * when reconciliation has NEVER succeeded the three value gauges read NaN,
     * while a merely stale cache still serves numbers.
     */
    private double reconciliationAgeSeconds() {
        Instant since = Instant.EPOCH.equals(cachedAt) ? startedAt : cachedAt;
        return Duration.between(since, clock.instant()).toMillis() / 1000.0;
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

        if (snapshot != null
                && Duration.between(cachedAt, clock.instant()).compareTo(reconciliationTtl) < 0) {
            return snapshot;
        }

        try {
            snapshot = reconciliationService.report();
            cachedReport = snapshot;
            cachedAt = clock.instant();
            return snapshot;
        } catch (RuntimeException e) {
            // Keep serving the last known values rather than a gap, but do not
            // refresh the timestamp: the next scrape retries.
            log.warn("Reference reconciliation query failed; serving last known values", e);
            return cachedReport;
        }
    }
}
