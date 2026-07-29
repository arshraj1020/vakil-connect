package com.arshraj.vakilconnect.reference.controller;

import com.arshraj.vakilconnect.reference.dto.CityResponse;
import com.arshraj.vakilconnect.reference.dto.CountryResponse;
import com.arshraj.vakilconnect.reference.dto.LanguageResponse;
import com.arshraj.vakilconnect.reference.dto.SpecializationResponse;
import com.arshraj.vakilconnect.reference.dto.StateResponse;
import com.arshraj.vakilconnect.reference.service.ReferenceDataService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Public reference vocabularies.
 *
 * Read-only and unauthenticated. Registration needs these lists before an
 * account exists, so requiring a token would make signup impossible; the data
 * is a curated vocabulary and discloses nothing about any user.
 *
 * CACHING is layered, and each layer does a different job:
 *
 *   1. `Cache-Control` below tells the browser and any CDN how long the payload
 *      stays fresh. `stale-while-revalidate` lets a client keep rendering an
 *      hours-old city list while it refreshes in the background - correct for
 *      data that changes quarterly.
 *
 *   2. `ETag`, added by the filter registered in ReferenceHttpCacheConfig, turns
 *      a revalidation into a 304 with no body.
 *
 *   3. `@Cacheable` on the service keeps the database out of it entirely.
 *
 * Every response is a bare List. These collections are bounded by curation - 36
 * states, ~200 cities, 23 languages - so pagination would add a wrapper and a
 * round trip for no benefit. The one unbounded surface, city search, is capped
 * by `limit` instead.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    /** Freshness for the near-static lists. */
    private static final CacheControl LONG_LIVED = CacheControl
            .maxAge(Duration.ofHours(24))
            .cachePublic()
            .staleWhileRevalidate(Duration.ofDays(7));

    /**
     * Search is user-driven and its result set shifts with the query, so it gets
     * a short window - enough to absorb the repeated requests a typeahead
     * generates, not enough to serve a stale list after an admin adds a city.
     */
    private static final CacheControl SHORT_LIVED = CacheControl
            .maxAge(Duration.ofMinutes(5))
            .cachePublic();

    private static final int DEFAULT_SEARCH_LIMIT = 10;

    private final ReferenceDataService referenceDataService;

    public ReferenceController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/countries")
    public ResponseEntity<List<CountryResponse>> countries() {
        return ResponseEntity.ok()
                .cacheControl(LONG_LIVED)
                .body(referenceDataService.getCountries());
    }

    /**
     * States of a country.
     *
     * Defaults to India rather than requiring the parameter: it is the only
     * seeded country, and a required parameter would make every caller send a
     * constant. The parameter exists so adding a second country needs no API
     * change.
     */
    @GetMapping("/states")
    public ResponseEntity<List<StateResponse>> states(
            @RequestParam(name = "countryIso2", defaultValue = "IN") String countryIso2) {

        return ResponseEntity.ok()
                .cacheControl(LONG_LIVED)
                .body(referenceDataService.getStates(countryIso2));
    }

    /**
     * Cities of one state - the dependent dropdown.
     *
     * `stateId` is required. Returning every city in the country would be a
     * ~200-entry list with no useful ordering, and the caller always knows the
     * state by the time this is reachable. Callers who do NOT know it use
     * /cities/search instead.
     *
     * An unknown `stateId` answers 400, not 404 and not an empty list. The
     * collection being addressed exists; the caller's argument is what is
     * wrong. See ReferenceDataServiceImpl#getCities for the full reasoning.
     */
    @GetMapping("/cities")
    public ResponseEntity<List<CityResponse>> cities(@RequestParam("stateId") UUID stateId) {
        return ResponseEntity.ok()
                .cacheControl(LONG_LIVED)
                .body(referenceDataService.getCities(stateId));
    }

    /**
     * Typeahead across city names and historical aliases.
     *
     * Alias coverage is the reason this endpoint exists separately: "Bangalore",
     * "Bombay" and "Gurgaon" are still what people type, and a picker that does
     * not resolve them is a worse experience than the free text it replaces.
     *
     * A blank query returns an empty list rather than the entire dataset.
     */
    @GetMapping("/cities/search")
    public ResponseEntity<List<CityResponse>> searchCities(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_SEARCH_LIMIT) int limit) {

        return ResponseEntity.ok()
                .cacheControl(SHORT_LIVED)
                .body(referenceDataService.searchCities(query, limit));
    }

    @GetMapping("/languages")
    public ResponseEntity<List<LanguageResponse>> languages() {
        return ResponseEntity.ok()
                .cacheControl(LONG_LIVED)
                .body(referenceDataService.getLanguages());
    }

    @GetMapping("/specializations")
    public ResponseEntity<List<SpecializationResponse>> specializations() {
        return ResponseEntity.ok()
                .cacheControl(LONG_LIVED)
                .body(referenceDataService.getSpecializations());
    }
}
