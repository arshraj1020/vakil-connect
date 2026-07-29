package com.arshraj.vakilconnect.reference.reconciliation;

import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports how much of the reference-link migration remains outstanding.
 *
 * STRICTLY READ-ONLY. It has no write path, no scheduler and - deliberately -
 * no controller: exposing migration internals over HTTP would be an operational
 * surface with no consumer, and any endpoint would need an authorization story
 * this phase does not have. It is reachable from tests, from a future admin
 * screen, or from a `main` shim.
 *
 * `@Transactional(readOnly = true)` is not decoration here: the aggregate is
 * built from several queries, and without one boundary a lawyer created between
 * the count and the name lookup would produce a report whose numbers do not add
 * up.
 *
 * On what the numbers mean: three of the eight figures are expected to stay at
 * their maximum indefinitely. `users` has never held a city or a language, and
 * `lawyers` has never held a language, so those V4 columns had nothing to
 * migrate from. They are reported anyway - a gap that is visible is a decision
 * waiting to be made, and a gap that is silent is one nobody remembers.
 */
@Service
public class ReferenceReconciliationService {

    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;

    public ReferenceReconciliationService(LawyerRepository lawyerRepository,
                                          UserRepository userRepository) {
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport report() {
        return new ReconciliationReport(
                lawyerRepository.count(),
                lawyerRepository.countByPrimaryCityIsNull(),
                lawyerRepository.findUnresolvedCityNames(),
                lawyerRepository.countByPracticeCitiesIsEmpty(),
                lawyerRepository.countByLanguagesIsEmpty(),
                userRepository.count(),
                userRepository.countByCityIsNull(),
                userRepository.countByPreferredLanguageIsNull());
    }
}
