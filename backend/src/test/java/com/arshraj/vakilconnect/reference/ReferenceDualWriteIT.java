package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.lawyer.repository.SpecializationRepository;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference dual-write (Phase 2E).
 *
 * Three things are under test:
 *
 *   1. V5 seeds the specialization vocabulary, so the reference endpoint is no
 *      longer dependent on a lawyer having registered first.
 *   2. Creating or updating a lawyer profile populates the reference links
 *      ALONGSIDE the legacy columns, which remain authoritative.
 *   3. Every existing request/response contract still behaves identically.
 *
 * Phase 2A covered the reference tables and 2B their mappings; neither is
 * repeated here.
 */
@DisplayName("Reference dual-write")
class ReferenceDualWriteIT extends AbstractIntegrationTest {

    private static final String PROFILE = "/api/lawyer/profile";
    private static final String SPECIALIZATIONS = "/api/reference/specializations";

    @Autowired private LawyerRepository lawyerRepository;
    @Autowired private SpecializationRepository specializationRepository;

    private Lawyer lawyerFor(String email) {
        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        return lawyerRepository.findByUser(user).orElseThrow();
    }

    /** The update payload, matching UpdateLawyerProfileRequest exactly. */
    private Map<String, Object> updateRequest(String city, List<String> specializations) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("experienceYears", 7);
        body.put("bio", "Updated professional bio");
        body.put("consultationFee", 2000);
        body.put("city", city);
        body.put("officeAddress", "9 Civil Lines");
        body.put("specializations", specializations);
        return body;
    }

    private Set<String> practiceCityNames(Lawyer lawyer) {
        return lawyerRepository.findByIdWithPracticeCities(lawyer.getId())
                .orElseThrow()
                .getPracticeCities().stream()
                .map(City::getName)
                .collect(Collectors.toSet());
    }

    // -------------------------------------------------------------- migration

    @Nested
    @DisplayName("V5 seed")
    class Seed {

        /**
         * The chicken-and-egg this migration exists to break: before V5 the
         * table was populated only by registration, so a fresh database served
         * an empty vocabulary and the first lawyer had nothing to select.
         */
        @Test
        @DisplayName("the curated vocabulary is seeded and served")
        void vocabularyIsSeeded() throws Exception {
            mockMvc.perform(get(SPECIALIZATIONS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'Family Law')]").isNotEmpty())
                    .andExpect(jsonPath("$[?(@.name == 'Criminal Law')]").isNotEmpty())
                    .andExpect(jsonPath("$[?(@.name == 'Intellectual Property')]").isNotEmpty());
        }

        /**
         * `name` is UNIQUE and real deployments already hold some of these rows
         * from earlier registrations, so the seed uses ON CONFLICT DO NOTHING.
         * A duplicate would mean the migration created a second "Family Law"
         * beside the one lawyers already point at.
         */
        @Test
        @DisplayName("seeding produced no duplicates")
        void seedIsIdempotentAgainstExistingRows() {
            List<String> names = specializationRepository.findAll().stream()
                    .map(s -> s.getName().toLowerCase())
                    .toList();

            assertEquals(names.size(), Set.copyOf(names).size(),
                    "duplicate specialization names: " + names);
        }
    }

    // ------------------------------------------------------------- dual-write

    @Nested
    @DisplayName("dual-write on create")
    class DualWriteOnCreate {

        /**
         * The heart of the phase: the legacy string is still written and still
         * authoritative, and the reference link is populated beside it.
         */
        @Test
        @DisplayName("registration writes both the legacy city and the reference link")
        void registrationWritesBoth() throws Exception {
            String email = uniqueEmail("dualwrite");
            registerAndLoginLawyer(email);

            Lawyer lawyer = lawyerFor(email);

            // Legacy column - unchanged, still what search reads.
            assertEquals("Mumbai", lawyer.getCity());

            // Reference link - new, populated in parallel.
            Lawyer withCity = lawyerRepository
                    .findByIdWithPrimaryCity(lawyer.getId()).orElseThrow();
            assertNotNull(withCity.getPrimaryCity(), "primary city should be linked");
            assertEquals("Mumbai", withCity.getPrimaryCity().getName());
            assertEquals("Maharashtra", withCity.getPrimaryCity().getState().getName());
        }

        /**
         * Option C's invariant: the primary city is also a member of the practice
         * set, so search can query one table without special-casing the primary.
         */
        @Test
        @DisplayName("the primary city is also a practice city")
        void primaryCityIsAlsoAPracticeCity() throws Exception {
            String email = uniqueEmail("dualwrite");
            registerAndLoginLawyer(email);

            assertEquals(Set.of("Mumbai"), practiceCityNames(lawyerFor(email)));
        }
    }

    @Nested
    @DisplayName("dual-write on update")
    class DualWriteOnUpdate {

        @Test
        @DisplayName("changing the city moves both the legacy value and the link")
        void updateMovesBoth() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Pune", List.of("Civil Law")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.city").value("Pune"));

            Lawyer lawyer = lawyerRepository
                    .findByIdWithPrimaryCity(lawyerFor(email).getId()).orElseThrow();

            assertEquals("Pune", lawyer.getCity());
            assertEquals("Pune", lawyer.getPrimaryCity().getName());
        }

        /**
         * The previous primary is removed as the new one is added, so repeated
         * edits do not accumulate phantom practice cities.
         */
        @Test
        @DisplayName("the old primary does not linger in the practice set")
        void oldPrimaryIsReplacedNotAccumulated() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Pune", List.of("Civil Law")))))
                    .andExpect(status().isOk());

            assertEquals(Set.of("Pune"), practiceCityNames(lawyerFor(email)));
        }

        /**
         * Historical names resolve exactly as the picker does, so a client
         * sending "Bombay" is linked to Mumbai rather than left unlinked.
         */
        @Test
        @DisplayName("a historical city name resolves through the alias table")
        void aliasResolves() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Bombay", List.of("Civil Law")))))
                    .andExpect(status().isOk())
                    /*
                     * Updated by Phase 2G. Until the read cut-over this asserted
                     * "Bombay", because the response echoed the legacy column.
                     * Reads now prefer the reference link, so the RESPONSE
                     * carries the canonical name while the COLUMN still holds
                     * what was sent - which the two assertions below pin
                     * separately. See ReferenceReadCutoverIT for the full
                     * cut-over behaviour.
                     */
                    .andExpect(jsonPath("$.city").value("Mumbai"));

            Lawyer lawyer = lawyerRepository
                    .findByIdWithPrimaryCity(lawyerFor(email).getId()).orElseThrow();

            assertEquals("Bombay", lawyer.getCity(), "legacy value is stored verbatim");
            assertEquals("Mumbai", lawyer.getPrimaryCity().getName(),
                    "alias should resolve to the current city");
        }

        /**
         * Best-effort, unlike specializations. The contract accepts any string
         * for `city` and this phase performs no backfill, so an unresolvable
         * name must leave the link empty rather than fail the request.
         */
        @Test
        @DisplayName("an unrecognised city still succeeds, leaving the link empty")
        void unresolvableCityDoesNotFail() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Mumbi", List.of("Civil Law")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.city").value("Mumbi"));

            assertEquals("Mumbi", lawyerFor(email).getCity());
        }

        /**
         * The invariant: the reference link must never CONTRADICT the legacy
         * string it mirrors.
         *
         * Keeping the old link here would make the system assert Mumbai for a
         * lawyer who said "Mumbi". Worse, reconciliation finds unmapped rows via
         * `primary_city_id IS NULL` - a stale non-null FK is invisible to it, so
         * the wrong link would survive reconciliation AND the read cut-over, and
         * put the lawyer in the wrong city's search results permanently.
         *
         * NULL means "not resolved" and contradicts nothing. Under-linking is
         * visible and fixable; mis-linking is neither.
         */
        @Test
        @DisplayName("an unresolvable name clears the stale link rather than keeping it")
        void unresolvableCityClearsTheStaleLink() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            // Registered as Mumbai, so a link already exists.
            assertNotNull(lawyerRepository
                    .findByIdWithPrimaryCity(lawyerFor(email).getId())
                    .orElseThrow().getPrimaryCity());

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Mumbi", List.of("Civil Law")))))
                    .andExpect(status().isOk());

            Lawyer lawyer = lawyerRepository
                    .findByIdWithPrimaryCity(lawyerFor(email).getId()).orElseThrow();

            assertEquals("Mumbi", lawyer.getCity(), "legacy value stored verbatim");
            assertNull(lawyer.getPrimaryCity(),
                    "the stale link must be cleared, not preserved");

            // Cleared from the join table too - leaving Mumbai there while the
            // primary is NULL would reintroduce the contradiction one table over.
            assertTrue(practiceCityNames(lawyerFor(email)).isEmpty(),
                    "the stale practice city must be cleared alongside the FK");
        }
    }

    // -------------------------------------------------------------- validation

    @Nested
    @DisplayName("authoritative specializations")
    class Validation {

        /**
         * The behavioural change of this phase. Previously this created a
         * "Wizardry" row and accepted the request; that row then appeared in the
         * public search filters. The endpoint is the vocabulary now.
         */
        @Test
        @DisplayName("an unknown practice area is rejected with 400")
        void unknownSpecializationIsRejected() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Pune", List.of("Wizardry")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            assertTrue(specializationRepository.findByNameIgnoreCase("Wizardry").isEmpty(),
                    "a rejected name must not be created as a side effect");
        }

        @Test
        @DisplayName("a known practice area is accepted, case-insensitively")
        void knownSpecializationIsAccepted() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(put(PROFILE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(updateRequest("Pune", List.of("criminal law")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.specializations[0]").value("Criminal Law"));
        }
    }

    // -------------------------------------------------- backward compatibility

    @Nested
    @DisplayName("backward compatibility")
    class BackwardCompatibility {

        /**
         * The response contract must be byte-for-byte what it was: no new
         * fields, no removed fields. Clients built against Phase 1 keep working.
         */
        @Test
        @DisplayName("the profile response shape is unchanged")
        void responseShapeIsUnchanged() throws Exception {
            String email = uniqueEmail("dualwrite");
            String token = registerAndLoginLawyer(email);

            mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.city").value("Mumbai"))
                    .andExpect(jsonPath("$.specializations").isArray())
                    .andExpect(jsonPath("$.barCouncilNumber").exists())
                    .andExpect(jsonPath("$.verified").value(false))
                    // The reference links are internal - they must NOT leak out.
                    .andExpect(jsonPath("$.primaryCity").doesNotExist())
                    .andExpect(jsonPath("$.primaryCityId").doesNotExist())
                    .andExpect(jsonPath("$.practiceCities").doesNotExist());
        }

        /**
         * Registration takes the same request body and answers the same way -
         * the dual-write is invisible to the caller.
         */
        @Test
        @DisplayName("registration accepts the unchanged request contract")
        void registrationContractIsUnchanged() throws Exception {
            String email = uniqueEmail("dualwrite");

            org.springframework.test.web.servlet.MvcResult result =
                    register(lawyerRegistration(email));

            assertEquals(201, result.getResponse().getStatus());
        }

        /** Client registration has no city or specializations and is untouched. */
        @Test
        @DisplayName("client registration is unaffected")
        void clientRegistrationIsUnaffected() throws Exception {
            String email = uniqueEmail("dualwrite-client");
            registerAndLoginClient(email);

            User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
            assertNotNull(user.getId());
            assertNull(lawyerRepository.findByUser(user).orElse(null));
        }
    }
}
