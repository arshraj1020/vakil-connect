package com.arshraj.vakilconnect.reference.service;

import com.arshraj.vakilconnect.common.util.TextNormalizer;
import com.arshraj.vakilconnect.lawyer.repository.SpecializationRepository;
import com.arshraj.vakilconnect.reference.dto.CityResponse;
import com.arshraj.vakilconnect.reference.dto.CountryResponse;
import com.arshraj.vakilconnect.reference.dto.LanguageResponse;
import com.arshraj.vakilconnect.reference.dto.SpecializationResponse;
import com.arshraj.vakilconnect.reference.dto.StateResponse;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.entity.CityAlias;
import com.arshraj.vakilconnect.reference.entity.State;
import com.arshraj.vakilconnect.reference.repository.CityAliasRepository;
import com.arshraj.vakilconnect.reference.repository.CityRepository;
import com.arshraj.vakilconnect.reference.repository.CountryRepository;
import com.arshraj.vakilconnect.reference.repository.LanguageRepository;
import com.arshraj.vakilconnect.reference.repository.StateRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reference data lookups.
 *
 * CACHING. These vocabularies change on the order of once a quarter and are
 * read on every registration and every search. Each list is cached under a
 * fixed key; the caches are only invalidated by a restart today, because no
 * write path exists yet - admin CRUD arrives in a later phase and will need
 * @CacheEvict.
 *
 * The DTOs are cached, not the entities. Caching entities would hand detached,
 * partially-initialised objects to later callers and defeat the transaction
 * boundary below.
 *
 * TRANSACTIONS. Every method is @Transactional(readOnly = true) because mapping
 * touches LAZY associations - `city.getState()` in particular. That is the exact
 * failure that produced the Phase 1 defect, so it is applied here by default
 * rather than added after something breaks.
 */
@Service
public class ReferenceDataServiceImpl implements ReferenceDataService {

    /** Guards the search endpoint against an unbounded IN-memory result set. */
    private static final int MAX_SEARCH_LIMIT = 50;

    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final CityAliasRepository cityAliasRepository;
    private final LanguageRepository languageRepository;
    private final SpecializationRepository specializationRepository;

    public ReferenceDataServiceImpl(CountryRepository countryRepository,
                                    StateRepository stateRepository,
                                    CityRepository cityRepository,
                                    CityAliasRepository cityAliasRepository,
                                    LanguageRepository languageRepository,
                                    SpecializationRepository specializationRepository) {
        this.countryRepository = countryRepository;
        this.stateRepository = stateRepository;
        this.cityRepository = cityRepository;
        this.cityAliasRepository = cityAliasRepository;
        this.languageRepository = languageRepository;
        this.specializationRepository = specializationRepository;
    }

    @Override
    @Cacheable("reference.countries")
    @Transactional(readOnly = true)
    public List<CountryResponse> getCountries() {
        return countryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new CountryResponse(c.getId(), c.getIso2(), c.getName(), c.getPhoneCode()))
                .toList();
    }

    /**
     * States of one country.
     *
     * Rejects an unknown country code with 400 for the same reason, and for
     * consistency: before this, an unknown `countryIso2` returned an empty list
     * while an unknown `stateId` returned an error - two behaviours for the same
     * class of argument, which is exactly the kind of inconsistency a client has
     * to write special cases around.
     */
    @Override
    @Cacheable("reference.states")
    @Transactional(readOnly = true)
    public List<StateResponse> getStates(String countryIso2) {
        if (countryRepository.findByIso2IgnoreCase(countryIso2).isEmpty()) {
            throw new IllegalArgumentException("Unknown country");
        }

        return stateRepository
                .findByCountryIso2IgnoreCaseAndActiveTrueOrderByNameAsc(countryIso2).stream()
                .map(s -> new StateResponse(s.getId(), s.getCode(), s.getName(), s.getType().name()))
                .toList();
    }

    /**
     * Cities of one state.
     *
     * The state is loaded ONCE and reused for every row rather than reading
     * `city.getState()` per city - one query instead of N+1.
     *
     * An unknown `stateId` is a 400, not a 404. The addressed resource is the
     * city collection, which exists; what is wrong is the caller's argument.
     * The client obtained this id from /api/reference/states moments earlier and
     * states are never deleted, so a value that does not resolve means a
     * malformed or stale request - a client error, not a missing resource.
     *
     * It is also not an empty list: silently returning nothing would render as
     * "this state has no cities", which looks like a data gap and hides a real
     * client bug.
     */
    @Override
    @Cacheable("reference.cities")
    @Transactional(readOnly = true)
    public List<CityResponse> getCities(UUID stateId) {
        State state = stateRepository.findById(stateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown state"));

        return cityRepository.findByStateIdAndActiveTrueOrderByNameAsc(stateId).stream()
                .map(c -> toResponse(c, state))
                .toList();
    }

    /**
     * Typeahead over city names and aliases.
     *
     * NOT cached. The key would be the caller's search term, which is unbounded
     * - every keystroke of every user would occupy a cache entry, and the
     * default ConcurrentMapCacheManager has no eviction policy. That is a memory
     * leak dressed as an optimisation. The GIN trigram index makes the query
     * cheap enough that caching buys little.
     *
     * Alias hits are merged with name hits and de-duplicated by city id, so a
     * search for "bombay" returns Mumbai exactly once even though it matches
     * through the alias table alone.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CityResponse> searchCities(String query, int limit) {
        String term = TextNormalizer.normalize(query);
        if (term == null || term.isBlank()) {
            return List.of();
        }

        int capped = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));

        // LinkedHashMap: de-duplicates by id while preserving name-ascending order.
        Map<UUID, CityResponse> merged = new LinkedHashMap<>();

        for (City city : cityRepository.searchByNormalizedTerm(term)) {
            merged.putIfAbsent(city.getId(), toResponse(city, city.getState()));
        }

        for (CityAlias alias : cityAliasRepository.findByAliasNormalizedWithCity(term)) {
            City city = alias.getCity();
            if (city.isActive()) {
                merged.putIfAbsent(city.getId(), toResponse(city, city.getState()));
            }
        }

        return new ArrayList<>(merged.values()).subList(0, Math.min(merged.size(), capped));
    }

    @Override
    @Cacheable("reference.languages")
    @Transactional(readOnly = true)
    public List<LanguageResponse> getLanguages() {
        return languageRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(l -> new LanguageResponse(
                        l.getId(), l.getIsoCode(), l.getName(), l.getNativeName()))
                .toList();
    }

    /**
     * Practice areas.
     *
     * `specializations` has no `active` column - it predates this design and is
     * still populated find-or-create at registration. Adding one would change
     * registration behaviour, which is out of scope for this phase, so every row
     * is returned and ordered by name.
     */
    @Override
    @Cacheable("reference.specializations")
    @Transactional(readOnly = true)
    public List<SpecializationResponse> getSpecializations() {
        return specializationRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(s -> new SpecializationResponse(s.getId(), s.getName()))
                .toList();
    }

    private CityResponse toResponse(City city, State state) {
        return new CityResponse(
                city.getId(),
                city.getName(),
                state.getId(),
                state.getCode(),
                state.getName());
    }
}
