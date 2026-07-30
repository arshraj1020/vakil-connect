package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.reference.metrics.FallbackReadSnapshot;
import com.arshraj.vakilconnect.reference.metrics.ReferenceFallbackMetrics;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference read cut-over (Phase 2G).
 *
 * The reference model becomes the PREFERRED read source; the legacy
 * `lawyers.city` string becomes a fallback. Four things are under test:
 *
 *   1. a linked lawyer's city is served from the reference, canonicalised
 *   2. an unlinked lawyer's city is served from the legacy column, verbatim
 *   3. search answers the same questions it did before, including for terms
 *      that only the alias table can resolve
 *   4. the fallback counters move, so "legacy reads have reached zero" is an
 *      observation rather than an assumption
 *
 * Dual-write (2E) and backfill (2F) are covered by their own classes and are
 * not repeated here.
 *
 * ISOLATION. Every test class shares one database and several of them register
 * lawyers in Mumbai, so nothing here asserts a count or a total. Assertions are
 * about ids this test created and cities this test chose - Nagpur (canonical),
 * Panaji/Panjim (an alias pair), and free text that resolves to nothing.
 */
@DisplayName("Reference read cut-over")
class ReferenceReadCutoverIT extends AbstractIntegrationTest {

    private static final String SEARCH = "/api/lawyers";

    /** Not a curated city, and never will be - the permanent fallback case. */
    private static final String UNRESOLVABLE_CITY = "Gotham";

    /**
     * A curated city seeded by V3 that NO other test in the suite uses, so the
     * per-row counting test can know the exact contents of its own result page.
     */
    private static final String COUNTING_CITY = "Kanpur";

    @Autowired private LawyerRepository lawyerRepository;
    @Autowired private ReferenceFallbackMetrics fallbackMetrics;

    // ---------------------------------------------------------------- helpers

    /**
     * Registers a lawyer in `city` and returns the persisted entity.
     *
     * distinctEmail, not uniqueEmail: several tests here seed two lawyers, and
     * uniqueEmail returns the same address twice within one test. The second
     * registration then 409s on the UNIQUE email, the lookup below returns the
     * FIRST lawyer, and the test quietly runs with two references to one row.
     *
     * The 201 assertion makes any future variant of that fail HERE, naming the
     * seed, rather than surfacing as an unexplained assertion three lines later.
     */
    private Lawyer seedLawyer(String city) throws Exception {
        String email = distinctEmail("cutover");

        assertEquals(201, register(lawyerRegistration(email, city)).getResponse().getStatus(),
                "seed registration must succeed for " + email);

        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        return lawyerRepository.findByUser(user).orElseThrow();
    }

    /**
     * As above, then verifies them.
     *
     * Search returns verified lawyers only, and a lawyer cannot self-verify, so
     * this is a precondition rather than behaviour under test.
     */
    private Lawyer seedVerifiedLawyer(String city) throws Exception {
        Lawyer lawyer = seedLawyer(city);
        lawyer.setVerified(true);
        return lawyerRepository.save(lawyer);
    }

    /** The `city` string a public profile response serves. */
    private String publicProfileCity(UUID lawyerId) throws Exception {
        String body = mockMvc.perform(get(SEARCH + "/" + lawyerId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("city").asText();
    }

    private JsonNode searchPage(String city, int size) throws Exception {
        MockHttpServletRequestBuilder request = get(SEARCH).param("size", String.valueOf(size));
        if (city != null) {
            request = request.param("city", city);
        }

        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * `totalElements`, from whichever Page serialization the framework is using.
     *
     * Spring Boot 3.5 serialises Page through PagedModel, which nests the
     * metadata under a "page" object; earlier versions put the same fields at
     * the top level. Which one is active is a framework default this phase does
     * not control and does not test, so the helper accepts both rather than
     * pinning these assertions to one of them.
     *
     * It does NOT silently default when neither is present - a page payload with
     * no total at all is a real problem and should fail loudly here.
     */
    private long totalElements(JsonNode page) {
        JsonNode flat = page.get("totalElements");
        if (flat != null) {
            return flat.asLong();
        }

        JsonNode nested = page.path("page").get("totalElements");
        assertNotNull(nested, "page payload exposes no totalElements in either shape: " + page);
        return nested.asLong();
    }

    /**
     * Every id matching a search, across the whole result set.
     *
     * Two requests, not one with a large `size`: this class runs after an
     * unknown number of other classes that also register and verify lawyers, so
     * any hard-coded page size would be a slow-growing flake. The probe reads
     * the total and the second request asks for exactly that many.
     */
    private List<UUID> searchIds(String city) throws Exception {
        long total = totalElements(searchPage(city, 1));

        List<UUID> ids = new ArrayList<>();
        if (total == 0) {
            return ids;
        }

        for (JsonNode node : searchPage(city, (int) total).get("content")) {
            ids.add(UUID.fromString(node.get("id").asText()));
        }
        return ids;
    }

    /** One lawyer's summary node out of a city-filtered search, or null if absent. */
    private JsonNode searchResultFor(String city, UUID lawyerId) throws Exception {
        long total = totalElements(searchPage(city, 1));
        if (total == 0) {
            return null;
        }

        for (JsonNode node : searchPage(city, (int) total).get("content")) {
            if (lawyerId.toString().equals(node.get("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // -------------------------------------------------------- prefer reference

    @Nested
    @DisplayName("reads prefer the reference")
    class PreferReference {

        /**
         * The cut-over in one assertion: the response no longer echoes the typed
         * string, it serves the canonical name behind the link.
         *
         * The input is deliberately badly cased and padded. Before this phase
         * the response was "  nAgPuR  " because it was `lawyers.city` verbatim.
         */
        @Test
        @DisplayName("a linked lawyer's city is served canonically, not as typed")
        void linkedLawyerServesCanonicalName() throws Exception {
            Lawyer lawyer = seedLawyer("  nAgPuR  ");

            assertEquals("Nagpur", publicProfileCity(lawyer.getId()));
        }

        /**
         * Alias resolution reaches the READ path for free, because the link the
         * dual-write created already points at the canonical city.
         */
        @Test
        @DisplayName("a lawyer who typed an alias is shown the canonical city")
        void aliasIsServedAsCanonical() throws Exception {
            Lawyer lawyer = seedLawyer("Panjim");

            assertEquals("Panaji", publicProfileCity(lawyer.getId()));
        }

        /**
         * The legacy column is a FALLBACK, not a mirror. Phase 2G explicitly
         * does not remove it or the dual-write, so the typed value must survive
         * untouched - it is what a rollback of this phase would read.
         */
        @Test
        @DisplayName("the legacy column still holds the value exactly as typed")
        void legacyColumnIsPreservedVerbatim() throws Exception {
            Lawyer lawyer = seedLawyer("  nAgPuR  ");

            assertEquals("  nAgPuR  ",
                    lawyerRepository.findById(lawyer.getId()).orElseThrow().getCity());
        }

        /** Both DTO mappers must resolve the city the same way. */
        @Test
        @DisplayName("search results canonicalise the city too, not just profiles")
        void summaryResponseAlsoPrefersReference() throws Exception {
            Lawyer lawyer = seedVerifiedLawyer("Panjim");

            JsonNode summary = searchResultFor("Panaji", lawyer.getId());

            assertNotNull(summary, "the seeded lawyer should be in the results");
            assertEquals("Panaji", summary.get("city").asText());
        }
    }

    // ---------------------------------------------------------------- fallback

    @Nested
    @DisplayName("fallback when no reference exists")
    class Fallback {

        /**
         * Nobody loses their city because the vocabulary is incomplete. An
         * unresolvable name has no link, so the read falls through to the
         * legacy column and the response is byte-identical to Phase 2F.
         */
        @Test
        @DisplayName("an unlinked lawyer's city is served from the legacy string")
        void unlinkedLawyerFallsBackToLegacy() throws Exception {
            Lawyer lawyer = seedLawyer(UNRESOLVABLE_CITY);

            assertNull(lawyerRepository.findByIdWithPrimaryCity(lawyer.getId())
                    .orElseThrow().getPrimaryCity(), "should not have resolved");

            assertEquals(UNRESOLVABLE_CITY, publicProfileCity(lawyer.getId()));
        }

        /**
         * The fallback is not a lossy round trip either: a lawyer whose free
         * text happens to be badly cased keeps that casing, because nothing
         * canonicalises a value that has no canonical form.
         */
        @Test
        @DisplayName("fallback preserves the typed casing")
        void fallbackDoesNotCanonicaliseFreeText() throws Exception {
            Lawyer lawyer = seedLawyer("gOtHaM");

            assertEquals("gOtHaM", publicProfileCity(lawyer.getId()));
        }
    }

    // ------------------------------------------------------- DTO compatibility

    @Nested
    @DisplayName("DTO compatibility")
    class DtoCompatibility {

        /**
         * The phase promises the external contract is unchanged. This pins the
         * profile payload's field set exactly, so adding, renaming or dropping
         * one during a later cut-over phase fails here rather than in a client.
         */
        @Test
        @DisplayName("the profile response exposes exactly the pre-cut-over fields")
        void profileResponseShapeIsUnchanged() throws Exception {
            Lawyer lawyer = seedLawyer("Nagpur");

            String body = mockMvc.perform(get(SEARCH + "/" + lawyer.getId()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertEquals(
                    Set.of("id", "fullName", "email", "phoneNumber", "barCouncilNumber",
                            "experienceYears", "bio", "consultationFee", "city",
                            "officeAddress", "verified", "rating", "totalReviews",
                            "specializations"),
                    fieldNames(objectMapper.readTree(body)));
        }

        @Test
        @DisplayName("the search summary exposes exactly the pre-cut-over fields")
        void summaryResponseShapeIsUnchanged() throws Exception {
            Lawyer lawyer = seedVerifiedLawyer("Nagpur");

            JsonNode mine = searchResultFor("Nagpur", lawyer.getId());

            assertNotNull(mine, "the seeded lawyer should be in the results");
            assertEquals(
                    Set.of("id", "fullName", "city", "experienceYears", "consultationFee",
                            "rating", "totalReviews", "specializations"),
                    fieldNames(mine));
        }

        /**
         * Specializations were never denormalised - `lawyer_specializations` has
         * been a join onto an entity since V1 - so there is nothing to cut over
         * and nothing may change. Still asserted, because the phase brief listed
         * specializations as a read target and a reader deserves the evidence.
         */
        @Test
        @DisplayName("specializations are unaffected and still serve names")
        void specializationsAreUnchanged() throws Exception {
            Lawyer lawyer = seedLawyer("Nagpur");

            mockMvc.perform(get(SEARCH + "/" + lawyer.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.specializations").isArray())
                    .andExpect(jsonPath("$.specializations[0]").value("Family Law"));
        }
    }

    // ----------------------------------------------------- search compatibility

    @Nested
    @DisplayName("search compatibility")
    class SearchCompatibility {

        @Test
        @DisplayName("a linked lawyer is found by the canonical city name")
        void canonicalNameFindsLinkedLawyer() throws Exception {
            Lawyer lawyer = seedVerifiedLawyer("Nagpur");

            assertTrue(searchIds("Nagpur").contains(lawyer.getId()));
        }

        /**
         * THE REGRESSION THIS PHASE COULD EASILY HAVE SHIPPED.
         *
         * Before the cut-over, "Panjim" matched `LOWER(lawyers.city)` and found
         * this lawyer. After it, the lawyer is linked to Panaji and their
         * normalised practice name is "panaji" - so a query matching the raw
         * term against the reference axis would find nothing, and the legacy
         * branch is closed to them because they ARE linked. They would silently
         * disappear from a search that used to return them.
         *
         * Resolving the search term through the alias table first is what
         * prevents that.
         */
        @Test
        @DisplayName("a lawyer linked via an alias is still found by that alias")
        void aliasStillFindsLinkedLawyer() throws Exception {
            Lawyer lawyer = seedVerifiedLawyer("Panjim");

            assertTrue(searchIds("Panjim").contains(lawyer.getId()),
                    "the alias search term must resolve to the linked city");
        }

        /** The symmetric case: the canonical term finds the alias-typed lawyer. */
        @Test
        @DisplayName("the canonical term finds a lawyer who typed the alias")
        void canonicalTermFindsAliasTypedLawyer() throws Exception {
            Lawyer lawyer = seedVerifiedLawyer("Panjim");

            assertTrue(searchIds("Panaji").contains(lawyer.getId()));
        }

        /**
         * The legacy branch still answers for rows the reference cannot. An
         * unresolvable term resolves to no city, so the query falls through to
         * `LOWER(lawyers.city)` exactly as before - which is the only way this
         * lawyer is reachable at all.
         */
        @Test
        @DisplayName("an unlinked lawyer is still found by their legacy string")
        void legacyStringStillFindsUnlinkedLawyer() throws Exception {
            Lawyer lawyer = seedVerifiedLawyer(UNRESOLVABLE_CITY);

            assertTrue(searchIds(UNRESOLVABLE_CITY).contains(lawyer.getId()));
        }

        /** The filter must still exclude. A cut-over that matches everything is worse than none. */
        @Test
        @DisplayName("a city filter does not leak lawyers from other cities")
        void filterStillExcludes() throws Exception {
            Lawyer inNagpur = seedVerifiedLawyer("Nagpur");
            Lawyer inGotham = seedVerifiedLawyer(UNRESOLVABLE_CITY);

            List<UUID> nagpurResults = searchIds("Nagpur");

            assertTrue(nagpurResults.contains(inNagpur.getId()));
            assertFalse(nagpurResults.contains(inGotham.getId()));
        }

        /**
         * An unlinked lawyer must not be dragged in by the reference branch
         * either. "Indore" resolves to a real city that this lawyer has no link
         * to, so neither branch may match them.
         */
        @Test
        @DisplayName("a curated city does not match an unrelated unlinked lawyer")
        void curatedTermDoesNotMatchUnlinkedLawyer() throws Exception {
            Lawyer inGotham = seedVerifiedLawyer(UNRESOLVABLE_CITY);

            assertFalse(searchIds("Indore").contains(inGotham.getId()));
        }

        /** Omitting the filter is unchanged: no city predicate at all. */
        @Test
        @DisplayName("searching without a city returns linked and unlinked lawyers alike")
        void noCityFilterIsUnaffected() throws Exception {
            Lawyer linked = seedVerifiedLawyer("Nagpur");
            Lawyer unlinked = seedVerifiedLawyer(UNRESOLVABLE_CITY);

            List<UUID> ids = searchIds(null);

            assertTrue(ids.contains(linked.getId()));
            assertTrue(ids.contains(unlinked.getId()));
        }
    }

    // ---------------------------------------------------------------- metrics

    @Nested
    @DisplayName("fallback metrics")
    class Metrics {

        /**
         * Deltas, never absolute values: the counters are process-scoped and
         * every preceding test class has already moved them.
         */
        @Test
        @DisplayName("a fallback read increments only the fallback counter")
        void fallbackReadIsCounted() throws Exception {
            Lawyer lawyer = seedLawyer(UNRESOLVABLE_CITY);

            FallbackReadSnapshot before = fallbackMetrics.snapshot();
            publicProfileCity(lawyer.getId());
            FallbackReadSnapshot after = fallbackMetrics.snapshot();

            assertEquals(before.cityFallbackReads() + 1, after.cityFallbackReads());
            assertEquals(before.cityReferenceReads(), after.cityReferenceReads());
        }

        @Test
        @DisplayName("a reference read increments only the reference counter")
        void referenceReadIsCounted() throws Exception {
            Lawyer lawyer = seedLawyer("Nagpur");

            FallbackReadSnapshot before = fallbackMetrics.snapshot();
            publicProfileCity(lawyer.getId());
            FallbackReadSnapshot after = fallbackMetrics.snapshot();

            assertEquals(before.cityReferenceReads() + 1, after.cityReferenceReads());
            assertEquals(before.cityFallbackReads(), after.cityFallbackReads());
        }

        /**
         * The counter is per DTO mapped, not per request - a page of results
         * records one read per row. That granularity is what makes the number
         * comparable to "how many served values still come from the legacy
         * column"; per-request counting would understate it by the page size.
         */
        @Test
        @DisplayName("a page of results counts one read per row, not one per request")
        void countingIsPerRowNotPerRequest() throws Exception {
            seedVerifiedLawyer(COUNTING_CITY);
            seedVerifiedLawyer(COUNTING_CITY);

            // ONE request, so the probe searchIds() issues cannot inflate the
            // delta, and the expected delta is read back out of that same
            // response rather than assumed.
            FallbackReadSnapshot before = fallbackMetrics.snapshot();
            JsonNode page = searchPage(COUNTING_CITY, 10);
            FallbackReadSnapshot after = fallbackMetrics.snapshot();

            int rows = page.get("content").size();

            // No other test seeds this city, so the page holds exactly the two
            // lawyers above. If that ever stops being true this assertion says
            // so directly, instead of the read-count assertion failing for a
            // reason that has nothing to do with counting.
            assertEquals(2, rows, "only this test seeds " + COUNTING_CITY);
            assertEquals(before.totalCityReads() + rows, after.totalCityReads(),
                    "each row on the page records one city read; a page is not one read");
        }

        /**
         * The signal the cut-over is aiming at. It must not read "complete"
         * while a single fallback is still happening.
         */
        @Test
        @DisplayName("completion is not claimed while fallbacks are still occurring")
        void completionIsNotClaimedWhileFallbacksRemain() throws Exception {
            Lawyer lawyer = seedLawyer(UNRESOLVABLE_CITY);
            publicProfileCity(lawyer.getId());

            assertFalse(fallbackMetrics.snapshot().cityLegacyReadsEliminated());
        }

        /**
         * Nor may it read "complete" on a process that has served nothing. Zero
         * fallbacks with zero reads is an absent measurement, not a finished
         * migration - and that distinction matters most at the moment someone
         * is deciding whether to drop the column.
         */
        @Test
        @DisplayName("a snapshot with no reads at all does not claim completion")
        void emptySnapshotDoesNotClaimCompletion() {
            assertFalse(new FallbackReadSnapshot(0, 0).cityLegacyReadsEliminated());
            assertTrue(new FallbackReadSnapshot(1, 0).cityLegacyReadsEliminated());
            assertFalse(new FallbackReadSnapshot(1, 1).cityLegacyReadsEliminated());
        }
    }
}
