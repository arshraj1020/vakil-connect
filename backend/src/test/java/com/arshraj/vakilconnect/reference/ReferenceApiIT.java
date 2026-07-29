package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.config.CachingConfig;
import com.arshraj.vakilconnect.reference.repository.StateRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public reference APIs (Phase 2C).
 *
 * Exercises the six endpoints end to end: security, payload shape, HTTP caching
 * and the service-level cache. The underlying data is already covered by
 * ReferenceDataIT (seed, uniqueness, aliases) and ReferenceLinkageIT (entity
 * mappings), so none of that is repeated - these tests are about the HTTP
 * surface.
 */
@DisplayName("Reference API")
class ReferenceApiIT extends AbstractIntegrationTest {

    private static final String COUNTRIES = "/api/reference/countries";
    private static final String STATES = "/api/reference/states";
    private static final String CITIES = "/api/reference/cities";
    private static final String CITY_SEARCH = "/api/reference/cities/search";
    private static final String LANGUAGES = "/api/reference/languages";
    private static final String SPECIALIZATIONS = "/api/reference/specializations";

    @Autowired private StateRepository stateRepository;
    @Autowired private CacheManager cacheManager;

    private UUID stateId(String code) {
        return stateRepository
                .findByCountryIso2IgnoreCaseAndCodeIgnoreCase("IN", code)
                .orElseThrow()
                .getId();
    }

    // -------------------------------------------------------------- security

    @Nested
    @DisplayName("security")
    class Security {

        /**
         * These must be reachable WITHOUT a token: registration needs the city
         * and specialization lists before an account exists, so requiring
         * authentication would make signup impossible.
         */
        @Test
        @DisplayName("every reference endpoint is reachable anonymously")
        void allEndpointsArePublic() throws Exception {
            mockMvc.perform(get(COUNTRIES)).andExpect(status().isOk());
            mockMvc.perform(get(STATES)).andExpect(status().isOk());
            mockMvc.perform(get(CITIES).param("stateId", stateId("MH").toString()))
                    .andExpect(status().isOk());
            mockMvc.perform(get(CITY_SEARCH).param("q", "mum")).andExpect(status().isOk());
            mockMvc.perform(get(LANGUAGES)).andExpect(status().isOk());
            mockMvc.perform(get(SPECIALIZATIONS)).andExpect(status().isOk());
        }

        /**
         * The matcher is scoped to GET so that adding a write endpoint later has
         * to be a deliberate security decision rather than an accident of the
         * path prefix.
         */
        @Test
        @DisplayName("a non-GET method is not opened up by the permitAll matcher")
        void nonGetIsNotPublic() throws Exception {
            mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .post(COUNTRIES))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ---------------------------------------------------------------- payload

    @Nested
    @DisplayName("payloads")
    class Payloads {

        @Test
        @DisplayName("countries returns India with its dialling code")
        void countriesReturnsIndia() throws Exception {
            mockMvc.perform(get(COUNTRIES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].iso2").value("IN"))
                    .andExpect(jsonPath("$[0].name").value("India"))
                    .andExpect(jsonPath("$[0].phoneCode").value("+91"))
                    .andExpect(jsonPath("$[0].id").exists());
        }

        @Test
        @DisplayName("states defaults to India and reports 36 with their type")
        void statesDefaultsToIndia() throws Exception {
            mockMvc.perform(get(STATES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(36))
                    .andExpect(jsonPath("$[?(@.code == 'MH')].name").value("Maharashtra"))
                    .andExpect(jsonPath("$[?(@.code == 'MH')].type").value("STATE"))
                    .andExpect(jsonPath("$[?(@.code == 'DL')].type").value("UNION_TERRITORY"));
        }

        @Test
        @DisplayName("an explicit country code is honoured")
        void statesAcceptsCountryParameter() throws Exception {
            mockMvc.perform(get(STATES).param("countryIso2", "in"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(36));
        }

        /**
         * The state is carried on every city even here, where the caller already
         * knows it - one payload shape serves both this endpoint and search, so
         * the frontend needs one component rather than two.
         */
        @Test
        @DisplayName("cities are scoped to the state and carry it")
        void citiesAreScopedToState() throws Exception {
            mockMvc.perform(get(CITIES).param("stateId", stateId("GA").toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'Panaji')]").isNotEmpty())
                    .andExpect(jsonPath("$[0].stateCode").value("GA"))
                    .andExpect(jsonPath("$[0].stateName").value("Goa"))
                    // Mumbai belongs to Maharashtra and must not leak in.
                    .andExpect(jsonPath("$[?(@.name == 'Mumbai')]").isEmpty());
        }

        /**
         * 400, not 404 and not an empty list.
         *
         * The addressed resource - the city collection - exists; the caller's
         * argument is what is wrong. Clients get this id from /states moments
         * earlier and states are never deleted, so an id that does not resolve
         * means a malformed or stale request.
         */
        @Test
        @DisplayName("an unknown state id is a client error, not a missing resource")
        void unknownStateIdReturns400() throws Exception {
            mockMvc.perform(get(CITIES).param("stateId", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        /**
         * The same rule for the same class of argument. Before this, an unknown
         * country returned an empty list while an unknown state returned an
         * error - two behaviours a client would have to special-case around.
         */
        @Test
        @DisplayName("an unknown country code is also a 400")
        void unknownCountryReturns400() throws Exception {
            mockMvc.perform(get(STATES).param("countryIso2", "ZZ"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("a missing stateId is a 400, not a full dump")
        void missingStateIdIsBadRequest() throws Exception {
            mockMvc.perform(get(CITIES)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("languages include native names and the 3-letter codes")
        void languagesIncludeNativeNames() throws Exception {
            mockMvc.perform(get(LANGUAGES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(23))
                    .andExpect(jsonPath("$[?(@.isoCode == 'mr')].nativeName").value("मराठी"))
                    // Santali has no ISO 639-1 code - the reason iso_code is varchar(3).
                    .andExpect(jsonPath("$[?(@.isoCode == 'sat')].name").value("Santali"));
        }

        @Test
        @DisplayName("specializations are returned for the picker")
        void specializationsAreReturned() throws Exception {
            // Ensure at least one exists, then clear the cache so this call
            // reflects the row just created rather than an earlier snapshot.
            registerAndLoginLawyer(uniqueEmail("spec"));
            evict(CachingConfig.SPECIALIZATIONS);

            mockMvc.perform(get(SPECIALIZATIONS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[?(@.name == 'Family Law')]").isNotEmpty());
        }
    }

    // ----------------------------------------------------------- city search

    @Nested
    @DisplayName("city search")
    class CitySearch {

        @Test
        @DisplayName("a partial name matches")
        void partialNameMatches() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "mumb"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'Mumbai')]").isNotEmpty())
                    .andExpect(jsonPath("$[?(@.name == 'Mumbai')].stateName").value("Maharashtra"));
        }

        /**
         * The reason this endpoint exists separately from the dropdown. Without
         * alias resolution a client typing "Bangalore" finds nothing and
         * concludes the platform is empty.
         */
        @Test
        @DisplayName("a historical name resolves to the current city")
        void historicalNameResolves() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "Bombay"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'Mumbai')]").isNotEmpty());

            mockMvc.perform(get(CITY_SEARCH).param("q", "bangalore"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'Bengaluru')]").isNotEmpty());
        }

        @Test
        @DisplayName("search is case- and whitespace-insensitive via normalisation")
        void searchIsNormalised() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "  BoMbAy  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'Mumbai')]").isNotEmpty());
        }

        @Test
        @DisplayName("a blank or missing query returns an empty list, not everything")
        void blankQueryReturnsEmpty() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "   "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            mockMvc.perform(get(CITY_SEARCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("the limit is respected and capped")
        void limitIsRespectedAndCapped() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "a").param("limit", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));

            // Far above MAX_SEARCH_LIMIT - must not blow up or return everything.
            mockMvc.perform(get(CITY_SEARCH).param("q", "a").param("limit", "9999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(50)));
        }

        /**
         * "Bombay" matches through the alias table only, but a term could match
         * both a name and an alias - the result must still list the city once.
         */
        @Test
        @DisplayName("name and alias hits are de-duplicated by city")
        void resultsAreDeduplicated() throws Exception {
            MvcResult result = mockMvc.perform(get(CITY_SEARCH).param("q", "mumbai"))
                    .andExpect(status().isOk())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            int occurrences = body.split("\"name\":\"Mumbai\"", -1).length - 1;
            assertEquals(1, occurrences, "Mumbai should appear exactly once: " + body);
        }
    }

    // ---------------------------------------------------------- http caching

    @Nested
    @DisplayName("HTTP caching")
    class HttpCaching {

        @Test
        @DisplayName("near-static lists advertise a long Cache-Control")
        void longLivedCacheControl() throws Exception {
            mockMvc.perform(get(COUNTRIES))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                            org.hamcrest.Matchers.containsString("max-age=86400")))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                            org.hamcrest.Matchers.containsString("public")));
        }

        @Test
        @DisplayName("search advertises a short Cache-Control")
        void shortLivedCacheControlOnSearch() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "pune"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                            org.hamcrest.Matchers.containsString("max-age=300")));
        }

        /**
         * The ETag filter is registered for /api/reference/* only, so this also
         * confirms the scoping worked - a global registration would have changed
         * the behaviour of every existing endpoint.
         */
        @Test
        @DisplayName("a repeat request with If-None-Match gets 304 and no body")
        void etagProduces304() throws Exception {
            MvcResult first = mockMvc.perform(get(LANGUAGES))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn();

            String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
            assertNotNull(etag);

            mockMvc.perform(get(LANGUAGES).header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("existing endpoints are unaffected by the scoped ETag filter")
        void existingEndpointsHaveNoEtag() throws Exception {
            mockMvc.perform(get("/api/lawyers"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG));
        }
    }

    // --------------------------------------------------------- service cache

    @Nested
    @DisplayName("service cache")
    class ServiceCache {

        @Test
        @DisplayName("a lookup populates its cache")
        void lookupPopulatesCache() throws Exception {
            evict(CachingConfig.LANGUAGES);
            assertNull(cachedNoArgValue(CachingConfig.LANGUAGES),
                    "cache should start empty after eviction");

            mockMvc.perform(get(LANGUAGES)).andExpect(status().isOk());

            assertNotNull(cachedNoArgValue(CachingConfig.LANGUAGES),
                    "@Cacheable should have populated " + CachingConfig.LANGUAGES);
        }

        /**
         * Search is deliberately NOT cached: its key would be the user's search
         * term, which is unbounded, and this cache manager has no eviction
         * policy. Caching it would be a memory leak dressed as an optimisation.
         */
        @Test
        @DisplayName("city search is not cached")
        void searchIsNotCached() throws Exception {
            mockMvc.perform(get(CITY_SEARCH).param("q", "kochi")).andExpect(status().isOk());

            assertEquals(null, cacheManager.getCache("reference.citySearch"),
                    "no cache should exist for city search");
        }

        @Test
        @DisplayName("every declared cache is registered")
        void declaredCachesExist() {
            for (String name : new String[]{
                    CachingConfig.COUNTRIES, CachingConfig.STATES, CachingConfig.CITIES,
                    CachingConfig.LANGUAGES, CachingConfig.SPECIALIZATIONS}) {
                assertNotNull(cacheManager.getCache(name), "missing cache: " + name);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private void evict(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "unknown cache: " + cacheName);
        cache.clear();
    }

    /**
     * Reads an entry through the Spring Cache API rather than the native store.
     *
     * Deliberately implementation-agnostic: Caffeine's native cache is a
     * Caffeine `Cache`, not a Map, so a probe that inspected `getNativeCache()`
     * would quietly stop working the moment the provider changed - reporting
     * "not cached" for a cache that was in fact populated.
     *
     * `SimpleKey.EMPTY` is what Spring's default key generator produces for a
     * no-argument @Cacheable method, which is how getLanguages() is keyed.
     */
    private Cache.ValueWrapper cachedNoArgValue(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "unknown cache: " + cacheName);
        return cache.get(SimpleKey.EMPTY);
    }
}
