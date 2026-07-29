package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.common.util.TextNormalizer;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.entity.CityAlias;
import com.arshraj.vakilconnect.reference.entity.Country;
import com.arshraj.vakilconnect.reference.entity.Language;
import com.arshraj.vakilconnect.reference.entity.State;
import com.arshraj.vakilconnect.reference.enums.AliasSource;
import com.arshraj.vakilconnect.reference.enums.StateType;
import com.arshraj.vakilconnect.reference.repository.CityAliasRepository;
import com.arshraj.vakilconnect.reference.repository.CityRepository;
import com.arshraj.vakilconnect.reference.repository.CountryRepository;
import com.arshraj.vakilconnect.reference.repository.LanguageRepository;
import com.arshraj.vakilconnect.reference.repository.StateRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reference data — schema, constraints and seed (Phase 2A).
 *
 * Runs against the real Flyway-migrated schema in the shared Postgres
 * container, so it verifies the V3 migration itself, not a Hibernate-generated
 * approximation of it. Because `ddl-auto: validate` is on, the mere fact that
 * this context starts already proves the entities and the migration agree.
 *
 * Reference tables are deliberately NOT linked to User or Lawyer in this phase,
 * so nothing here touches those fixtures.
 */
@DisplayName("Reference data")
class ReferenceDataIT extends AbstractIntegrationTest {

    private static final String INDIA = "IN";

    @Autowired private CountryRepository countryRepository;
    @Autowired private StateRepository stateRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private CityAliasRepository cityAliasRepository;
    @Autowired private LanguageRepository languageRepository;

    private State maharashtra() {
        return stateRepository
                .findByCountryIso2IgnoreCaseAndCodeIgnoreCase(INDIA, "MH")
                .orElseThrow();
    }

    // ------------------------------------------------------- seed integrity

    @Nested
    @DisplayName("seed integrity")
    class SeedIntegrity {

        @Test
        @DisplayName("India is seeded, and it is the only country")
        void indiaIsTheOnlySeededCountry() {
            List<Country> all = countryRepository.findAll();
            assertEquals(1, all.size(), "only India should be seeded");

            Country india = countryRepository.findByIso2IgnoreCase(INDIA).orElseThrow();
            assertEquals("India", india.getName());
            assertEquals("IND", india.getIso3());
            assertEquals("+91", india.getPhoneCode());
            assertTrue(india.isActive());
        }

        @Test
        @DisplayName("all 36 states and union territories are seeded (28 + 8)")
        void allStatesAndUnionTerritoriesAreSeeded() {
            List<State> states =
                    stateRepository.findByCountryIso2IgnoreCaseAndActiveTrueOrderByNameAsc(INDIA);

            assertEquals(36, states.size());
            assertEquals(28, states.stream().filter(s -> s.getType() == StateType.STATE).count());
            assertEquals(8, states.stream()
                    .filter(s -> s.getType() == StateType.UNION_TERRITORY).count());
        }

        @Test
        @DisplayName("every state has at least one city")
        void everyStateHasAtLeastOneCity() {
            List<State> states =
                    stateRepository.findByCountryIso2IgnoreCaseAndActiveTrueOrderByNameAsc(INDIA);

            List<String> empty = states.stream()
                    .filter(s -> cityRepository.countByStateId(s.getId()) == 0)
                    .map(State::getName)
                    .toList();

            assertTrue(empty.isEmpty(), "states seeded with no cities: " + empty);
        }

        @Test
        @DisplayName("the 22 scheduled languages plus English are seeded, with native names")
        void languagesAreSeeded() {
            List<Language> languages = languageRepository.findByActiveTrueOrderByNameAsc();
            assertEquals(23, languages.size());

            languages.forEach(l -> {
                assertNotNull(l.getNativeName());
                assertFalse(l.getNativeName().isBlank(),
                        l.getName() + " should carry a native name");
            });

            // Codes longer than two characters are why iso_code is varchar(3):
            // Santali has no ISO 639-1 code.
            assertTrue(languageRepository.findByIsoCodeIgnoreCase("sat").isPresent());
            assertTrue(languageRepository.findByIsoCodeIgnoreCase("hi").isPresent());
        }

        @Test
        @DisplayName("every seeded name_normalized matches TextNormalizer")
        void seededNormalisedNamesMatchTheAlgorithm() {
            List<City> mismatched = cityRepository.findAll().stream()
                    .filter(c -> !c.getNameNormalized()
                            .equals(TextNormalizer.normalize(c.getName())))
                    .toList();

            assertTrue(mismatched.isEmpty(),
                    "cities whose seeded normalised name disagrees with the algorithm: "
                            + mismatched.stream().map(City::getName).toList());
        }
    }

    // --------------------------------------------------- hierarchy integrity

    @Nested
    @DisplayName("hierarchy integrity")
    class HierarchyIntegrity {

        @Test
        @DisplayName("city resolves through state to country")
        void cityResolvesToCountry() {
            City mumbai = cityRepository
                    .findByStateIdAndNameNormalized(maharashtra().getId(), "mumbai")
                    .orElseThrow();

            City loaded = cityRepository.findByIdWithHierarchy(mumbai.getId()).orElseThrow();

            assertEquals("Mumbai", loaded.getName());
            assertEquals("Maharashtra", loaded.getState().getName());
            assertEquals(INDIA, loaded.getState().getCountry().getIso2());
        }

        @Test
        @DisplayName("every state belongs to the seeded country")
        void everyStateBelongsToIndia() {
            assertEquals(36, stateRepository.countByCountryIso2IgnoreCase(INDIA));
            assertEquals(36, stateRepository.count(),
                    "no state should exist outside the seeded country");
        }

        @Test
        @DisplayName("a city cannot be created without a state")
        void cityRequiresAState() {
            City orphan = new City();
            orphan.setName("Nowhere");
            orphan.setNameNormalized("nowhere");

            assertThrows(Exception.class, () -> cityRepository.saveAndFlush(orphan),
                    "state_id is NOT NULL and must be enforced");
        }
    }

    // ------------------------------------------------------------ uniqueness

    @Nested
    @DisplayName("uniqueness constraints")
    class Uniqueness {

        @Test
        @DisplayName("the same normalised city name cannot repeat within one state")
        void duplicateCityInSameStateIsRejected() {
            City duplicate = new City();
            duplicate.setState(maharashtra());
            duplicate.setName("Mumbai");
            duplicate.setNameNormalized(TextNormalizer.normalize("Mumbai"));

            assertThrows(DataIntegrityViolationException.class,
                    () -> cityRepository.saveAndFlush(duplicate));
        }

        /**
         * The constraint that makes normalisation worth having: without it,
         * "  MUMBAI " is a different string from "Mumbai" and both would be
         * stored - which is exactly the free-text problem this table replaces.
         */
        @Test
        @DisplayName("a differently-cased or padded duplicate is also rejected")
        void casingAndPaddingDoNotEvadeTheConstraint() {
            City duplicate = new City();
            duplicate.setState(maharashtra());
            duplicate.setName("  MUMBAI  ");
            duplicate.setNameNormalized(TextNormalizer.normalize("  MUMBAI  "));

            assertThrows(DataIntegrityViolationException.class,
                    () -> cityRepository.saveAndFlush(duplicate));
        }

        /**
         * Uniqueness is scoped to the state on purpose: several Indian city
         * names repeat across states. A global constraint would reject real data.
         */
        @Test
        @DisplayName("the same city name IS allowed in a different state")
        void sameNameInAnotherStateIsAllowed() {
            State bihar = stateRepository
                    .findByCountryIso2IgnoreCaseAndCodeIgnoreCase(INDIA, "BR").orElseThrow();

            City aurangabadBihar = new City();
            aurangabadBihar.setState(bihar);
            aurangabadBihar.setName("Aurangabad");
            aurangabadBihar.setNameNormalized(TextNormalizer.normalize("Aurangabad"));

            City saved = cityRepository.saveAndFlush(aurangabadBihar);
            assertNotNull(saved.getId());

            cityRepository.delete(saved);
            cityRepository.flush();
        }

        @Test
        @DisplayName("a country's ISO code cannot repeat")
        void duplicateCountryIsoIsRejected() {
            Country duplicate = new Country();
            duplicate.setIso2("IN");
            duplicate.setIso3("XXX");
            duplicate.setName("Duplicate");
            duplicate.setPhoneCode("+00");

            assertThrows(DataIntegrityViolationException.class,
                    () -> countryRepository.saveAndFlush(duplicate));
        }

        @Test
        @DisplayName("a language ISO code cannot repeat")
        void duplicateLanguageIsoIsRejected() {
            Language duplicate = new Language();
            duplicate.setIsoCode("hi");
            duplicate.setName("Hindi Duplicate");
            duplicate.setNativeName("हिन्दी");

            assertThrows(DataIntegrityViolationException.class,
                    () -> languageRepository.saveAndFlush(duplicate));
        }

        @Test
        @DisplayName("the same alias cannot be attached to one city twice")
        void duplicateAliasOnSameCityIsRejected() {
            City mumbai = cityRepository
                    .findByStateIdAndNameNormalized(maharashtra().getId(), "mumbai")
                    .orElseThrow();

            CityAlias duplicate = new CityAlias();
            duplicate.setCity(mumbai);
            duplicate.setAlias("Bombay");
            duplicate.setAliasNormalized(TextNormalizer.normalize("Bombay"));
            duplicate.setSource(AliasSource.ADMIN);

            assertThrows(DataIntegrityViolationException.class,
                    () -> cityAliasRepository.saveAndFlush(duplicate));
        }
    }

    // ---------------------------------------------------------- alias lookup

    @Nested
    @DisplayName("alias lookup")
    class AliasLookup {

        @Test
        @DisplayName("a historical name resolves to its current city")
        void historicalNameResolves() {
            List<CityAlias> found =
                    cityAliasRepository.findByAliasNormalizedWithCity("bombay");

            assertEquals(1, found.size());
            assertEquals("Mumbai", found.get(0).getCity().getName());
            assertEquals("Maharashtra", found.get(0).getCity().getState().getName());
            assertEquals(AliasSource.SEED, found.get(0).getSource());
        }

        @Test
        @DisplayName("the renames users actually still type all resolve")
        void commonRenamesResolve() {
            record Rename(String alias, String expectedCity) {
            }

            List<Rename> renames = List.of(
                    new Rename("bombay", "Mumbai"),
                    new Rename("calcutta", "Kolkata"),
                    new Rename("madras", "Chennai"),
                    new Rename("bangalore", "Bengaluru"),
                    new Rename("gurgaon", "Gurugram"),
                    new Rename("allahabad", "Prayagraj"),
                    new Rename("trivandrum", "Thiruvananthapuram"),
                    new Rename("pondicherry", "Puducherry"),
                    new Rename("aurangabad", "Chhatrapati Sambhajinagar"));

            renames.forEach(r -> {
                List<CityAlias> found =
                        cityAliasRepository.findByAliasNormalizedWithCity(r.alias());
                assertFalse(found.isEmpty(), "no city found for alias: " + r.alias());
                assertEquals(r.expectedCity(), found.get(0).getCity().getName(),
                        "wrong city for alias: " + r.alias());
            });
        }

        @Test
        @DisplayName("an unknown alias resolves to nothing rather than failing")
        void unknownAliasReturnsEmpty() {
            assertTrue(cityAliasRepository
                    .findByAliasNormalizedWithCity("atlantis").isEmpty());
        }

        @Test
        @DisplayName("alias lookup uses the normalised form, so casing does not matter")
        void aliasLookupIsCaseInsensitiveViaNormalisation() {
            String normalised = TextNormalizer.normalize("  BoMbAy ");
            assertEquals("bombay", normalised);
            assertFalse(cityAliasRepository
                    .findByAliasNormalizedWithCity(normalised).isEmpty());
        }
    }

    // ----------------------------------------------------------- city search

    @Nested
    @DisplayName("city search")
    class CitySearch {

        @Test
        @DisplayName("an infix fragment matches, with the hierarchy resolved")
        void infixFragmentMatches() {
            List<City> results = cityRepository.searchByNormalizedTerm("mumb");

            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(c -> "Mumbai".equals(c.getName())));
            // JOIN FETCH means this does not trigger a lazy load per row.
            results.forEach(c -> assertNotNull(c.getState().getCountry().getIso2()));
        }

        @Test
        @DisplayName("the dependent dropdown returns only that state's cities, sorted")
        void dependentDropdownIsScopedAndSorted() {
            List<City> goa = cityRepository.findByStateIdAndActiveTrueOrderByNameAsc(
                    stateRepository
                            .findByCountryIso2IgnoreCaseAndCodeIgnoreCase(INDIA, "GA")
                            .orElseThrow().getId());

            assertFalse(goa.isEmpty());
            assertTrue(goa.stream().anyMatch(c -> "Panaji".equals(c.getName())));

            List<String> names = goa.stream().map(City::getName).toList();
            assertEquals(names.stream().sorted().toList(), names, "should be name-ascending");
        }

        @Test
        @DisplayName("exact lookup is scoped to the state")
        void exactLookupIsScopedToState() {
            Optional<City> inMaharashtra = cityRepository
                    .findByStateIdAndNameNormalized(maharashtra().getId(), "mumbai");
            assertTrue(inMaharashtra.isPresent());

            State goa = stateRepository
                    .findByCountryIso2IgnoreCaseAndCodeIgnoreCase(INDIA, "GA").orElseThrow();
            assertTrue(cityRepository
                    .findByStateIdAndNameNormalized(goa.getId(), "mumbai").isEmpty());
        }
    }
}
