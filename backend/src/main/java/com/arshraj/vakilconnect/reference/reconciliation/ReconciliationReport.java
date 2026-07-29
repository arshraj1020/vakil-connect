package com.arshraj.vakilconnect.reference.reconciliation;

import java.util.List;

/**
 * The state of the reference-link migration.
 *
 * Read-only snapshot. Its purpose is to answer one question before anyone
 * considers cutting reads over to the reference columns: is anything still
 * unmapped, and if so, what?
 *
 * @param totalLawyers                  lawyers in the system
 * @param lawyersMissingPrimaryCity     lawyers whose `city` string did not resolve
 * @param unresolvedCityNames           the distinct legacy strings that failed -
 *                                      the actionable list, since each is either
 *                                      a typo, a city to seed, or an alias to add
 * @param lawyersMissingPracticeCities  lawyers with an empty practice set
 * @param lawyersMissingLanguages       lawyers with no languages (expected to be
 *                                      all of them - no legacy source exists)
 * @param totalUsers                    users in the system
 * @param usersMissingCity              users with no city (expected to be all)
 * @param usersMissingPreferredLanguage users with no language (expected to be all)
 */
public record ReconciliationReport(
        long totalLawyers,
        long lawyersMissingPrimaryCity,
        List<String> unresolvedCityNames,
        long lawyersMissingPracticeCities,
        long lawyersMissingLanguages,
        long totalUsers,
        long usersMissingCity,
        long usersMissingPreferredLanguage) {

    /**
     * Whether the CITY migration is complete.
     *
     * Deliberately narrow. It reports only on what V6 could actually migrate -
     * the lawyer city links - because those are the only reference columns with
     * a legacy source. Languages and user cities have never held data, so
     * including them would make this permanently false and therefore useless as
     * a gate.
     */
    public boolean cityBackfillComplete() {
        return lawyersMissingPrimaryCity == 0 && lawyersMissingPracticeCities == 0;
    }

    /** One-line summary for a log line or an operator's console. */
    public String summary() {
        return "lawyers=%d missingPrimaryCity=%d missingPracticeCities=%d unresolvedNames=%d"
                .formatted(totalLawyers, lawyersMissingPrimaryCity,
                        lawyersMissingPracticeCities, unresolvedCityNames.size());
    }
}
