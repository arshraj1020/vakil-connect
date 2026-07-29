package com.arshraj.vakilconnect.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caching for reference data.
 *
 * Caffeine rather than a plain ConcurrentHashMap, and the reason is defensive
 * rather than performance. What is cached today is trivially small and bounded
 * by curation - one country, one entry each for states, languages and
 * specializations, and at most ~36 city lists - so an unbounded map would work
 * fine NOW. What it would not do is survive the two mistakes most likely to be
 * made later:
 *
 *   1. Someone adds @Cacheable to a method keyed on user input. That is an
 *      unbounded key space, and with no eviction it is a memory leak that only
 *      appears under real traffic. `maximumSize` makes it structurally
 *      impossible rather than merely discouraged. City search is deliberately
 *      NOT cached for exactly this reason - see
 *      ReferenceDataServiceImpl#searchCities - but that is a comment, and a
 *      comment is not a mechanism.
 *
 *   2. Admin CRUD arrives and a @CacheEvict is missed on one path. Without
 *      expiry that means stale reference data until the next restart;
 *      `expireAfterWrite` bounds the damage to a day.
 *
 * Sizing. 500 entries against a real working set of roughly 40 leaves ample room
 * for a second country without ever approaching the bound - it is a guard rail,
 * not a tuning parameter. The 24-hour TTL matches the `Cache-Control` max-age
 * the controller advertises, so the server-side and client-side layers expire in
 * step rather than one serving content the other already considers stale.
 *
 * The cache names are declared here as constants and passed to the manager, so
 * they are visible in one place - and they double as the checklist for the
 * @CacheEvict work that admin CRUD will require.
 *
 * In-process, not distributed. Redis would buy coherent eviction across
 * instances, which matters only once there is more than one instance AND a write
 * path; neither exists yet.
 */
@Configuration
@EnableCaching
public class CachingConfig {

    public static final String COUNTRIES = "reference.countries";
    public static final String STATES = "reference.states";
    public static final String CITIES = "reference.cities";
    public static final String LANGUAGES = "reference.languages";
    public static final String SPECIALIZATIONS = "reference.specializations";

    /** A bound, not a tuning parameter - see the class comment. */
    private static final long MAX_ENTRIES = 500;

    /** Matches the Cache-Control max-age on the reference endpoints. */
    private static final Duration TTL = Duration.ofHours(24);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                COUNTRIES, STATES, CITIES, LANGUAGES, SPECIALIZATIONS);

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(TTL));

        return cacheManager;
    }
}
