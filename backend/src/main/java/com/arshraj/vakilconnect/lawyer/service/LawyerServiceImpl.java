package com.arshraj.vakilconnect.lawyer.service;

import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.dto.LawyerProfileResponse;
import com.arshraj.vakilconnect.lawyer.dto.LawyerSummaryResponse;
import com.arshraj.vakilconnect.lawyer.dto.UpdateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.entity.Specialization;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.lawyer.repository.SpecializationRepository;
import com.arshraj.vakilconnect.common.util.TextNormalizer;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.entity.CityAlias;
import com.arshraj.vakilconnect.reference.metrics.ReferenceFallbackMetrics;
import com.arshraj.vakilconnect.reference.repository.CityAliasRepository;
import com.arshraj.vakilconnect.reference.repository.CityRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LawyerServiceImpl implements LawyerService {

    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;
    private final SpecializationRepository specializationRepository;
    private final CityRepository cityRepository;
    private final CityAliasRepository cityAliasRepository;
    private final ReferenceFallbackMetrics fallbackMetrics;

    public LawyerServiceImpl(LawyerRepository lawyerRepository,
                             UserRepository userRepository,
                             SpecializationRepository specializationRepository,
                             CityRepository cityRepository,
                             CityAliasRepository cityAliasRepository,
                             ReferenceFallbackMetrics fallbackMetrics) {
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
        this.specializationRepository = specializationRepository;
        this.cityRepository = cityRepository;
        this.cityAliasRepository = cityAliasRepository;
        this.fallbackMetrics = fallbackMetrics;
    }

    /*
     * Same reasoning as verifyLawyer: this method also ends by calling
     * toProfileResponse, which touches the LAZY specializations collection.
     *
     * It had not surfaced in practice because registration creates the Lawyer
     * through AuthServiceImpl, which is annotated @Transactional at class
     * level - this endpoint (POST /api/lawyer/profile) is the other, rarer path
     * to the same mapping. Annotating it closes the latent defect and also makes
     * the several writes here (specialization resolution, then the lawyer
     * insert) atomic rather than independently committed.
     */
    @Override
    @Transactional
    public LawyerProfileResponse createLawyerProfile(
            String userEmail,
            CreateLawyerProfileRequest request) {

        // Find logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != Role.LAWYER) {
            throw new BusinessRuleException("Only users registered as LAWYER can create a lawyer profile.");
        }

        // Prevent duplicate lawyer profile
        if (lawyerRepository.existsByUser(user)) {
            throw new DuplicateResourceException("Lawyer profile already exists.");
        }

        // Prevent duplicate Bar Council Number
        if (lawyerRepository.existsByBarCouncilNumber(request.getBarCouncilNumber())) {
            throw new DuplicateResourceException("Bar Council Number already registered.");
        }

        // Create Lawyer entity
        Lawyer lawyer = new Lawyer();

        lawyer.setUser(user);
        lawyer.setBarCouncilNumber(request.getBarCouncilNumber());
        lawyer.setExperienceYears(request.getExperienceYears());
        lawyer.setBio(request.getBio());
        lawyer.setConsultationFee(request.getConsultationFee());
        lawyer.setCity(request.getCity());
        lawyer.setOfficeAddress(request.getOfficeAddress());
        lawyer.setSpecializations(resolveSpecializations(request.getSpecializations()));

        // Dual-write: the legacy `city` string set above stays authoritative;
        // the reference link is populated beside it for a later cut-over.
        applyCityReference(lawyer, request.getCity());

        // Save Lawyer
        Lawyer savedLawyer = lawyerRepository.save(lawyer);

        return toProfileResponse(savedLawyer);
    }

    /*
     * readOnly transactions keep the persistence context open while the
     * entities are mapped to DTOs. Lawyer.specializations is a LAZY
     * @ManyToMany and open-in-view is disabled, so without a surrounding
     * transaction the session closes when the repository call returns and
     * mapping the collection throws LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<LawyerSummaryResponse> searchLawyers(
            String keyword,
            String specialization,
            String city,
            BigDecimal minFee,
            BigDecimal maxFee,
            Integer minExperience,
            Double minRating,
            Pageable pageable) {

        String cityFilter = blankToNull(city);

        /*
         * Phase 2G: resolve the search TERM before querying, using the same
         * alias-aware resolver the dual-write uses. Two reasons it happens here
         * rather than in SQL:
         *
         *   1. It is the only way "Bombay" keeps finding the lawyer who typed
         *      "Bombay" and is now linked to Mumbai. Their normalised practice
         *      name is "mumbai"; matching the raw term would silently drop them.
         *   2. Resolution is the same rule as the write path, so a term either
         *      names a curated city or it does not - no second, subtly
         *      different matching rule expressed in JPQL.
         *
         * An unresolvable or ambiguous term yields null, which confines the
         * query to the legacy branch. That is the never-guess rule from 2E/2F:
         * an ambiguous name matches nothing on the reference axis rather than
         * having one city picked for it.
         *
         * Cost is one or two indexed lookups per search that carries a city
         * filter. Deliberately not cached: the city vocabulary is cached at the
         * ReferenceDataService layer, and adding a second cache keyed on
         * arbitrary user input would be an unbounded key space - the same reason
         * city SEARCH is uncached there.
         */
        String cityNormalized = resolveCity(cityFilter)
                .map(City::getNameNormalized)
                .orElse(null);

        Page<Lawyer> lawyers = lawyerRepository.search(
                blankToNull(keyword),
                blankToNull(specialization),
                cityFilter,
                cityNormalized,
                minFee,
                maxFee,
                minExperience,
                minRating,
                pageable
        );

        return lawyers.map(this::toSummaryResponse);
    }

    /*
     * readOnly for the same reason as the other reads: Lawyer.specializations is
     * a LAZY @ManyToMany and open-in-view is disabled, so mapping it outside a
     * transaction would throw LazyInitializationException.
     *
     * The lookup deliberately mirrors updateCurrentLawyerProfile exactly, so the
     * profile a lawyer reads is by construction the one their next PUT writes.
     */
    @Override
    @Transactional(readOnly = true)
    public LawyerProfileResponse getCurrentLawyerProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lawyer lawyer = lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));

        return toProfileResponse(lawyer);
    }

    @Override
    @Transactional(readOnly = true)
    public LawyerProfileResponse getLawyerProfile(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        return toProfileResponse(lawyer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LawyerSummaryResponse> getPendingLawyers(Pageable pageable) {
        return lawyerRepository.findByVerifiedFalse(pageable)
                .map(this::toSummaryResponse);
    }

    /*
     * @Transactional is required, not decorative.
     *
     * Without it, findById and save each ran in their own transaction and the
     * entity was detached by the time toProfileResponse mapped it. That method
     * reads `lawyer.getSpecializations()` - the only LAZY association in the
     * domain - and with open-in-view disabled the read threw
     * LazyInitializationException AFTER the save had already committed.
     *
     * The visible effect was the worst kind: the lawyer really was verified,
     * but the admin saw HTTP 500 "An unexpected error occurred" and had no way
     * to tell the write had succeeded.
     *
     * Read-write, not readOnly: this method writes.
     */
    @Override
    @Transactional
    public LawyerProfileResponse verifyLawyer(UUID lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found"));

        lawyer.setVerified(true);

        return toProfileResponse(lawyerRepository.save(lawyer));
    }

    @Override
    @Transactional
    public LawyerProfileResponse updateCurrentLawyerProfile(
            String userEmail, UpdateLawyerProfileRequest request) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lawyer lawyer = lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));

        lawyer.setExperienceYears(request.getExperienceYears());
        lawyer.setBio(request.getBio());
        lawyer.setConsultationFee(request.getConsultationFee());
        lawyer.setCity(request.getCity());
        lawyer.setOfficeAddress(request.getOfficeAddress());
        // Reuses the exact specialization resolution used at registration -
        // which is now resolve-or-reject against the seeded vocabulary.
        lawyer.setSpecializations(resolveSpecializations(request.getSpecializations()));

        // Dual-write, same contract as creation.
        applyCityReference(lawyer, request.getCity());

        return toProfileResponse(lawyerRepository.save(lawyer));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /* ------------------------------------------------ read cut-over (2G) --
     *
     * The single place a lawyer's city is turned into the string clients see.
     * Both DTO mappers below go through it, so the preference order is defined
     * once and cannot drift between the profile and the search card.
     */

    /**
     * Reference first, legacy string as fallback.
     *
     * WHAT CHANGES FOR CLIENTS. The field, its type and its position are
     * untouched - `city` is still a plain string on the same responses. What
     * changes for a LINKED lawyer is that the value is now the CANONICAL name
     * rather than whatever was typed: "  mumbai " becomes "Mumbai", and
     * "Bombay" becomes "Mumbai". That canonicalisation is the entire point of
     * the cut-over; a read that returned the free text unchanged would leave
     * the reference model decorative. It is also mostly invisible in practice,
     * because since Phase 2D the frontend writes city names through the picker,
     * so the typed value and the canonical value are already the same string.
     *
     * WHAT DOES NOT CHANGE. An UNLINKED lawyer - unresolvable free text, or a
     * row the backfill could not match - is returned exactly as before, byte for
     * byte. Nobody loses their city because the vocabulary is incomplete.
     *
     * The fallback is counted rather than logged. Each call is one read, so the
     * counters are per-DTO, not per-request: a page of ten search results
     * records ten. That is the intended granularity - the question is what
     * share of served values still come from the legacy column.
     *
     * TRANSACTIONS. `primaryCity` is a LAZY @ManyToOne and open-in-view is
     * disabled, so every caller of this method must already be inside a
     * transaction. All of them are, for the same reason they already had to be
     * for `specializations`. Paged callers additionally fetch it through an
     * @EntityGraph on the repository, so this does not become an N+1.
     */
    private String cityOf(Lawyer lawyer) {
        City primaryCity = lawyer.getPrimaryCity();

        if (primaryCity != null) {
            fallbackMetrics.recordCityReferenceRead();
            return primaryCity.getName();
        }

        fallbackMetrics.recordCityFallbackRead();
        return lawyer.getCity();
    }

    private LawyerProfileResponse toProfileResponse(Lawyer lawyer) {
        LawyerProfileResponse response = new LawyerProfileResponse();

        response.setId(lawyer.getId());
        response.setFullName(lawyer.getUser().getFullName());
        response.setEmail(lawyer.getUser().getEmail());
        response.setPhoneNumber(lawyer.getUser().getPhoneNumber());

        response.setBarCouncilNumber(lawyer.getBarCouncilNumber());
        response.setExperienceYears(lawyer.getExperienceYears());
        response.setBio(lawyer.getBio());
        response.setConsultationFee(lawyer.getConsultationFee());
        response.setCity(cityOf(lawyer));
        response.setOfficeAddress(lawyer.getOfficeAddress());

        response.setVerified(lawyer.getVerified());
        response.setRating(lawyer.getRating());
        response.setTotalReviews(lawyer.getTotalReviews());
        response.setSpecializations(
                lawyer.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList())
        );

        return response;
    }

    private LawyerSummaryResponse toSummaryResponse(Lawyer lawyer) {
        LawyerSummaryResponse response = new LawyerSummaryResponse();

        response.setId(lawyer.getId());
        response.setFullName(lawyer.getUser().getFullName());
        response.setCity(cityOf(lawyer));
        response.setExperienceYears(lawyer.getExperienceYears());
        response.setConsultationFee(lawyer.getConsultationFee());
        response.setRating(lawyer.getRating());
        response.setTotalReviews(lawyer.getTotalReviews());
        response.setSpecializations(
                lawyer.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList())
        );

        return response;
    }

    /**
     * Resolves specialization NAMES against the curated vocabulary.
     *
     * Resolve-or-REJECT. This method used to be resolve-or-CREATE, which meant
     * every registration could silently mint a new specialization: a direct API
     * call with "Wizardry" created that row, and it then appeared in the public
     * search filters. The vocabulary was only ever enforced client-side, by a
     * constant the backend knew nothing about.
     *
     * V5 seeds that constant into the database, which is what makes rejecting
     * safe: there is now always something to resolve against, so the first
     * lawyer on a fresh deployment is not locked out.
     *
     * IllegalArgumentException maps to 400 through GlobalExceptionHandler, so an
     * unknown name is reported as a client error on the offending field rather
     * than accepted and quietly denormalised. The request/response CONTRACT is
     * unchanged - still `specializations: string[]` in and out; only invalid
     * input is now refused instead of absorbed.
     */
    private Set<Specialization> resolveSpecializations(List<String> names) {
        Set<Specialization> specializations = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();

        for (String rawName : names) {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty()) {
                continue;
            }

            specializationRepository.findByNameIgnoreCase(name)
                    .ifPresentOrElse(specializations::add, () -> unknown.add(name));
        }

        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown practice area(s): " + String.join(", ", unknown));
        }

        return specializations;
    }

    /* --------------------------------------------------- reference dual-write --
     *
     * Phase 2E writes the reference relationships ALONGSIDE the legacy columns.
     * The legacy `city` string remains authoritative and is still what search
     * reads; `primary_city_id` and `lawyer_practice_cities` are populated in
     * parallel so a later phase can cut over with the data already in place.
     */

    /**
     * Best-effort resolution of a free-text city name to a curated city.
     *
     * Deliberately BEST-EFFORT, unlike specializations. The contract accepts any
     * string for `city`, existing rows contain arbitrary text, and this phase
     * performs no backfill - so a name that does not resolve must leave the
     * reference link empty rather than fail the request. Rejecting here would
     * break the very clients this phase promises not to disturb.
     *
     * Falls back to the alias table, so "Bombay" and "Bangalore" resolve to
     * Mumbai and Bengaluru exactly as the picker does.
     *
     * Every seeded city name is unique across states, so a normalised match is
     * unambiguous; if that ever stops being true, an ambiguous match resolves to
     * nothing rather than guessing a state.
     */
    private Optional<City> resolveCity(String cityName) {
        String normalized = TextNormalizer.normalize(cityName);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }

        List<City> byName = cityRepository.findByNameNormalizedAndActiveTrue(normalized);
        if (byName.size() == 1) {
            return Optional.of(byName.get(0));
        }
        if (byName.size() > 1) {
            // Ambiguous without a state - link nothing rather than pick one.
            return Optional.empty();
        }

        List<CityAlias> byAlias =
                cityAliasRepository.findByAliasNormalizedWithCity(normalized);
        List<City> active = byAlias.stream()
                .map(CityAlias::getCity)
                .filter(City::isActive)
                .toList();

        return active.size() == 1 ? Optional.of(active.get(0)) : Optional.empty();
    }

    /**
     * Keeps the reference link in lockstep with the legacy `city` string.
     *
     * THE INVARIANT: the reference link must never contradict the legacy value
     * it mirrors. NULL means "not resolved" and contradicts nothing; a stale FK
     * contradicts directly.
     *
     * So an unresolvable name CLEARS the link rather than preserving the
     * previous one. Preserving looks protective and is not: if a lawyer changes
     * their city to something the vocabulary does not contain, keeping the old
     * link makes the system assert a city the lawyer never claimed. Worse, the
     * reconciliation phase finds unmapped rows with `primary_city_id IS NULL` -
     * a stale non-null FK is invisible to it, so the wrong link would survive
     * reconciliation, survive the read cut-over, and put the lawyer in the wrong
     * city's search results permanently.
     *
     * Under-linking is visible and fixable. Mis-linking is neither.
     *
     * The practice set is cleared alongside the FK, not just the FK: leaving the
     * old city in `lawyer_practice_cities` while the primary is NULL would
     * reintroduce the same contradiction one table over. Cities added by a
     * future multi-city UI survive, because only the PRIMARY is swapped out.
     *
     * Maintains the Option C invariant throughout - whenever a primary city
     * exists, it is also a member of the practice set.
     */
    private void applyCityReference(Lawyer lawyer, String cityName) {
        Optional<City> resolved = resolveCity(cityName);
        City previousPrimary = lawyer.getPrimaryCity();

        boolean primaryChanged = previousPrimary != null
                && (resolved.isEmpty()
                    || !previousPrimary.getId().equals(resolved.get().getId()));

        if (primaryChanged) {
            lawyer.getPracticeCities().remove(previousPrimary);
        }

        if (resolved.isPresent()) {
            City city = resolved.get();
            lawyer.setPrimaryCity(city);
            lawyer.getPracticeCities().add(city);
        } else {
            lawyer.setPrimaryCity(null);
        }
    }
}