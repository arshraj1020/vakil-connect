package com.arshraj.vakilconnect.reference.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * Counts how often a read still reaches the legacy column (Phase 2G).
 *
 * WHY COUNTERS AND NOT LOGGING. A log line per fallback would emit one record
 * per lawyer per search result page - thousands of lines describing a single
 * condition, which is how a useful signal becomes an ignored one. The question
 * being asked is "how many", and the answer is a number.
 *
 * WHY NOT MICROMETER. Micrometer is not on the classpath: this project has no
 * actuator dependency and no meter registry. Adding one would mean a new
 * dependency, a new HTTP surface and an authorization decision about who may
 * read it - all outside this phase. These counters are shaped so that swapping
 * the LongAdders for Micrometer `Counter`s later is a change to this class only;
 * no call site mentions the implementation.
 *
 * WHY NO ENDPOINT. Same reasoning as the Phase 2F reconciliation service: an
 * HTTP surface needs an authorization story this phase does not have, and has
 * no consumer yet. The counters are readable by any injected collaborator and
 * are asserted directly in the integration tests.
 *
 * ---------------------------------------------------------------------------
 * WHY ONLY CITY IS INSTRUMENTED
 *
 * Phase 2G named four counters. Only one of them can ever be non-zero, because
 * only one of the four targets has a legacy read path to fall back to:
 *
 *   cityFallbackReads             lawyers.city (varchar, V1)      INSTRUMENTED
 *   specializationFallbackReads   no legacy column - the          not applicable
 *                                 lawyer_specializations join
 *                                 table has been the reference
 *                                 model since V1
 *   userCityFallbackReads         users has never held a city     not applicable
 *   userLanguageFallbackReads     users has never held a language not applicable
 *
 * A counter that no code path can increment is dead code that reads as
 * evidence: a dashboard showing `userCityFallbackReads: 0` would look like a
 * completed migration rather than an absent one. So the three are not defined
 * here. When a legacy source for any of them appears, the counter is added
 * beside `cityFallbackReads` and wired at the mapping site, the same way
 * `cityOf` is wired in LawyerServiceImpl.
 * ---------------------------------------------------------------------------
 *
 * THREAD SAFETY. LongAdder, not AtomicLong: these are written on every request
 * thread and read rarely, which is precisely the contention profile LongAdder
 * exists for.
 */
@Component
public class ReferenceFallbackMetrics {

    private final LongAdder cityReferenceReads = new LongAdder();
    private final LongAdder cityFallbackReads = new LongAdder();

    /** A lawyer's city was served from `primary_city_id`. */
    public void recordCityReferenceRead() {
        cityReferenceReads.increment();
    }

    /** A lawyer's city had no reference link and was served from `lawyers.city`. */
    public void recordCityFallbackRead() {
        cityFallbackReads.increment();
    }

    /**
     * A consistent-enough reading of both counters.
     *
     * Not atomic across the two adders - a concurrent read can land between
     * them. That is acceptable and not worth a lock: the counters are compared
     * against zero and against each other in orders of magnitude, never for an
     * exact accounting.
     */
    public FallbackReadSnapshot snapshot() {
        return new FallbackReadSnapshot(cityReferenceReads.sum(), cityFallbackReads.sum());
    }
}
