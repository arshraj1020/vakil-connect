package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.entity.Language;
import com.arshraj.vakilconnect.reference.entity.State;
import com.arshraj.vakilconnect.reference.repository.CityRepository;
import com.arshraj.vakilconnect.reference.repository.LanguageRepository;
import com.arshraj.vakilconnect.reference.repository.StateRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reference-data linkage — entity mappings and join tables (Phase 2B).
 *
 * Covers only what V4 added: the associations from Lawyer and User onto the
 * reference tables. Phase 2A already tested the reference tables themselves
 * (seed, uniqueness, aliases, hierarchy) and none of that is repeated here.
 *
 * Nothing in the application reads these associations yet, so these tests are
 * the only thing exercising them until the cut-over phase.
 */
@DisplayName("Reference data linkage")
class ReferenceLinkageIT extends AbstractIntegrationTest {

    private static final String INDIA = "IN";

    @Autowired private LawyerRepository lawyerRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private StateRepository stateRepository;
    @Autowired private LanguageRepository languageRepository;
    @Autowired private EntityManager entityManager;

    private City city(String stateCode, String normalizedName) {
        State state = stateRepository
                .findByCountryIso2IgnoreCaseAndCodeIgnoreCase(INDIA, stateCode)
                .orElseThrow();
        return cityRepository
                .findByStateIdAndNameNormalized(state.getId(), normalizedName)
                .orElseThrow();
    }

    private Language language(String isoCode) {
        return languageRepository.findByIsoCodeIgnoreCase(isoCode).orElseThrow();
    }

    /** Registers a lawyer through the real API and returns the managed entity. */
    private Lawyer seedLawyer() throws Exception {
        String email = uniqueEmail("linkage");
        registerAndLoginLawyer(email);
        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        return lawyerRepository.findByUser(user).orElseThrow();
    }

    // ------------------------------------------------- nullable by default

    @Nested
    @DisplayName("nullable relationships")
    class NullableRelationships {

        /**
         * The whole point of V4 being additive: existing rows, and rows created
         * by the untouched registration path, must remain valid with every new
         * association empty.
         */
        @Test
        @DisplayName("a newly registered lawyer has no primary city, cities or languages")
        void newLawyerHasNoReferenceLinks() throws Exception {
            Lawyer lawyer = seedLawyer();

            assertNull(lawyerRepository.findByIdWithPrimaryCity(lawyer.getId())
                    .orElseThrow().getPrimaryCity());
            assertTrue(lawyerRepository.findByIdWithPracticeCities(lawyer.getId())
                    .orElseThrow().getPracticeCities().isEmpty());
            assertTrue(lawyerRepository.findByIdWithLanguages(lawyer.getId())
                    .orElseThrow().getLanguages().isEmpty());
        }

        @Test
        @DisplayName("a newly registered user has no city or preferred language")
        @Transactional
        void newUserHasNoReferenceLinks() throws Exception {
            String email = uniqueEmail("linkage-user");
            registerAndLoginClient(email);

            User user = userRepositoryForSupport.findByEmail(email).orElseThrow();

            assertNull(user.getCity());
            assertNull(user.getPreferredLanguage());
        }

        @Test
        @DisplayName("registration still succeeds untouched by the new columns")
        void registrationIsUnaffected() throws Exception {
            Lawyer lawyer = seedLawyer();

            // The free-text column remains authoritative in this phase.
            assertEquals("Mumbai", lawyer.getCity());
            assertNotNull(lawyer.getBarCouncilNumber());
        }
    }

    // ------------------------------------------------------------ persistence

    @Nested
    @DisplayName("persistence and join tables")
    class Persistence {

        @Test
        @DisplayName("primary city persists and resolves through to its state")
        void primaryCityPersists() throws Exception {
            Lawyer lawyer = seedLawyer();
            City mumbai = city("MH", "mumbai");

            lawyer.setPrimaryCity(mumbai);
            lawyerRepository.saveAndFlush(lawyer);

            Lawyer reloaded = lawyerRepository
                    .findByIdWithPrimaryCity(lawyer.getId()).orElseThrow();

            assertEquals("Mumbai", reloaded.getPrimaryCity().getName());
            assertEquals("Maharashtra", reloaded.getPrimaryCity().getState().getName());
        }

        /**
         * Option C in practice: a Delhi NCR advocate covering three cities.
         * This is the case that made @ManyToOne alone the wrong model.
         */
        @Test
        @DisplayName("a lawyer persists several practice cities across states")
        void practiceCitiesPersistAcrossStates() throws Exception {
            Lawyer lawyer = seedLawyer();

            City delhi = city("DL", "delhi");
            City gurugram = city("HR", "gurugram");
            City noida = city("UP", "noida");

            lawyer.setPrimaryCity(delhi);
            lawyer.setPracticeCities(new HashSet<>(Set.of(delhi, gurugram, noida)));
            lawyerRepository.saveAndFlush(lawyer);

            Lawyer reloaded = lawyerRepository
                    .findByIdWithPracticeCities(lawyer.getId()).orElseThrow();

            assertEquals(3, reloaded.getPracticeCities().size());
            assertEquals(Set.of("Delhi", "Gurugram", "Noida"),
                    reloaded.getPracticeCities().stream().map(City::getName)
                            .collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("a lawyer persists several languages")
        void languagesPersist() throws Exception {
            Lawyer lawyer = seedLawyer();

            lawyer.setLanguages(new HashSet<>(Set.of(language("en"), language("hi"), language("mr"))));
            lawyerRepository.saveAndFlush(lawyer);

            Lawyer reloaded = lawyerRepository
                    .findByIdWithLanguages(lawyer.getId()).orElseThrow();

            assertEquals(3, reloaded.getLanguages().size());
            assertTrue(reloaded.getLanguages().stream()
                    .anyMatch(l -> "Marathi".equals(l.getName())));
        }

        @Test
        @DisplayName("user city and preferred language persist")
        @Transactional
        void userReferencesPersist() throws Exception {
            String email = uniqueEmail("linkage-user");
            registerAndLoginClient(email);

            User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
            user.setCity(city("KA", "bengaluru"));
            user.setPreferredLanguage(language("kn"));
            userRepositoryForSupport.saveAndFlush(user);

            entityManager.flush();
            entityManager.clear();

            User reloaded = userRepositoryForSupport.findByEmail(email).orElseThrow();
            assertEquals("Bengaluru", reloaded.getCity().getName());
            assertEquals("Kannada", reloaded.getPreferredLanguage().getName());
        }

        /**
         * The join table is the owning side, so removing a member must delete
         * the row rather than orphan it.
         */
        @Test
        @DisplayName("removing a practice city deletes only the join row")
        void removingAPracticeCityDeletesTheJoinRow() throws Exception {
            Lawyer lawyer = seedLawyer();
            City pune = city("MH", "pune");
            City nashik = city("MH", "nashik");

            lawyer.setPracticeCities(new HashSet<>(Set.of(pune, nashik)));
            lawyerRepository.saveAndFlush(lawyer);

            Lawyer loaded = lawyerRepository
                    .findByIdWithPracticeCities(lawyer.getId()).orElseThrow();
            loaded.setPracticeCities(new HashSet<>(Set.of(pune)));
            lawyerRepository.saveAndFlush(loaded);

            Lawyer reloaded = lawyerRepository
                    .findByIdWithPracticeCities(lawyer.getId()).orElseThrow();

            assertEquals(1, reloaded.getPracticeCities().size());
            // The city itself is shared reference data and must survive.
            assertTrue(cityRepository.findById(nashik.getId()).isPresent());
        }
    }

    // ----------------------------------------------------------- lazy loading

    @Nested
    @DisplayName("lazy loading")
    class LazyLoading {

        /**
         * Proves the collections really are LAZY rather than silently eager.
         *
         * This is the assertion that matters most on this class: the Phase 1
         * defect was a lazy association touched outside a transaction, and the
         * only way to know the guard is needed is to demonstrate the failure.
         */
        @Test
        @DisplayName("touching practiceCities outside a transaction throws")
        void practiceCitiesAreLazy() throws Exception {
            Lawyer lawyer = seedLawyer();
            lawyer.setPracticeCities(new HashSet<>(Set.of(city("MH", "pune"))));
            lawyerRepository.saveAndFlush(lawyer);

            // Plain findById - no fetch join, no open session afterwards.
            Lawyer detached = lawyerRepository.findById(lawyer.getId()).orElseThrow();

            assertThrows(LazyInitializationException.class,
                    () -> detached.getPracticeCities().size(),
                    "practiceCities must be LAZY - an eager mapping would silently "
                            + "join on every lawyer query");
        }

        @Test
        @DisplayName("the fetch-join loader resolves the collection for detached use")
        void fetchJoinResolvesPracticeCities() throws Exception {
            Lawyer lawyer = seedLawyer();
            lawyer.setPracticeCities(new HashSet<>(Set.of(city("MH", "pune"))));
            lawyerRepository.saveAndFlush(lawyer);

            Lawyer fetched = lawyerRepository
                    .findByIdWithPracticeCities(lawyer.getId()).orElseThrow();

            // No exception, and the nested state is resolved too.
            assertEquals(1, fetched.getPracticeCities().size());
            fetched.getPracticeCities()
                    .forEach(c -> assertNotNull(c.getState().getName()));
        }

        @Test
        @DisplayName("inside a transaction the collection initialises on access")
        @Transactional
        void collectionsInitialiseInsideATransaction() throws Exception {
            Lawyer lawyer = seedLawyer();
            lawyer.setLanguages(new HashSet<>(Set.of(language("hi"))));
            lawyerRepository.saveAndFlush(lawyer);

            entityManager.clear();

            Lawyer loaded = lawyerRepository.findById(lawyer.getId()).orElseThrow();
            assertEquals(1, loaded.getLanguages().size());
        }
    }

    // ------------------------------------------------------ cascade behaviour

    @Nested
    @DisplayName("cascade behaviour")
    class CascadeBehaviour {

        /**
         * The association carries no cascade, deliberately. Cities and languages
         * are shared reference rows - a cascade would let removing one lawyer
         * delete Mumbai for everyone.
         */
        @Test
        @DisplayName("deleting a lawyer removes join rows but never the reference rows")
        void deletingALawyerLeavesReferenceDataIntact() throws Exception {
            Lawyer lawyer = seedLawyer();
            City pune = city("MH", "pune");
            Language hindi = language("hi");
            UUID lawyerId = lawyer.getId();

            lawyer.setPracticeCities(new HashSet<>(Set.of(pune)));
            lawyer.setLanguages(new HashSet<>(Set.of(hindi)));
            lawyerRepository.saveAndFlush(lawyer);

            lawyerRepository.deleteById(lawyerId);
            lawyerRepository.flush();

            assertFalse(lawyerRepository.findById(lawyerId).isPresent());
            assertTrue(cityRepository.findById(pune.getId()).isPresent(),
                    "the city is shared reference data and must survive");
            assertTrue(languageRepository.findById(hindi.getId()).isPresent(),
                    "the language is shared reference data and must survive");
        }

        /**
         * ON DELETE RESTRICT on the reference side. Reference rows are
         * deactivated, never deleted, and this is what enforces that when a
         * lawyer still points at one.
         */
        @Test
        @DisplayName("a city still referenced by a lawyer cannot be deleted")
        void referencedCityCannotBeDeleted() throws Exception {
            Lawyer lawyer = seedLawyer();
            City kochi = city("KL", "kochi");

            lawyer.setPrimaryCity(kochi);
            lawyerRepository.saveAndFlush(lawyer);

            assertThrows(Exception.class, () -> {
                cityRepository.deleteById(kochi.getId());
                cityRepository.flush();
            }, "the FK is ON DELETE RESTRICT");
        }
    }
}
