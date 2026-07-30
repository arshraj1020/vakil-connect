package com.arshraj.vakilconnect.reference.metrics;

/**
 * An immutable reading of the fallback counters (Phase 2G).
 *
 * Taken at a point in time so a caller can compare two readings and reason
 * about a delta. The counters themselves are monotonic and process-scoped -
 * they are a migration signal, not a business metric, and they reset when the
 * application restarts. That is deliberate: the question they answer is "is
 * this deployment still serving legacy values", which is only meaningful for
 * the running process.
 *
 * @param cityReferenceReads number of lawyer city reads served from the
 *                           reference model since startup
 * @param cityFallbackReads  number of lawyer city reads that fell back to the
 *                           legacy `lawyers.city` string since startup
 */
public record FallbackReadSnapshot(long cityReferenceReads, long cityFallbackReads) {

    public long totalCityReads() {
        return cityReferenceReads + cityFallbackReads;
    }

    /**
     * Whether the legacy city column can be considered read-dead.
     *
     * Requires a non-zero reference count as well as a zero fallback count.
     * Without the denominator, a freshly started process that has served no
     * traffic at all would report the migration complete - which is exactly the
     * wrong answer at exactly the moment someone is deciding whether to drop the
     * column.
     */
    public boolean cityLegacyReadsEliminated() {
        return cityFallbackReads == 0 && cityReferenceReads > 0;
    }

    public String summary() {
        return """
                Reference fallback reads (since startup)
                  lawyer city, from reference : %d
                  lawyer city, from legacy    : %d
                  legacy city reads eliminated: %s"""
                .formatted(cityReferenceReads, cityFallbackReads, cityLegacyReadsEliminated());
    }
}
