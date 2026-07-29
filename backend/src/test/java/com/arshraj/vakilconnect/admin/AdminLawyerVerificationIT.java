package com.arshraj.vakilconnect.admin;

import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin lawyer verification.
 *
 * The first integration test of the admin module. Its absence is why the
 * lazy-loading defect below reached manual testing: every other admin endpoint
 * is still untested, and nothing exercised this one.
 *
 * THE REGRESSION being guarded: `verifyLawyer` had no @Transactional, so
 * `findById` and `save` each ran in their own transaction and the entity was
 * detached by the time `toProfileResponse` mapped it. That mapping reads
 * `Lawyer.specializations` - the only LAZY association in the domain - and with
 * open-in-view disabled it threw LazyInitializationException AFTER the write had
 * committed. The lawyer really was verified; the admin saw HTTP 500.
 *
 * A test asserting only `status().isOk()` would NOT have caught it (the failure
 * was a 500, so it would have) - but more importantly, a test that never reads
 * `specializations` from the response would not prove the collection was
 * initialised inside the transaction. That assertion is the point of
 * {@link #verifyResponseCarriesTheFullProfileIncludingSpecializations()}.
 */
@DisplayName("Admin lawyer verification")
class AdminLawyerVerificationIT extends AbstractIntegrationTest {

    private static final String PENDING_ENDPOINT = "/api/admin/lawyers/pending";

    /**
     * Large enough that a freshly registered lawyer is certainly on the first
     * page. Test classes share one container and never clean up, so unverified
     * lawyers from other classes accumulate in this queue; a default page of 10
     * would make these assertions order-dependent and flaky.
     */
    private static final String PENDING_ALL = PENDING_ENDPOINT + "?page=0&size=500";

    @Autowired
    private LawyerRepository lawyerRepository;

    /** Registers a lawyer and returns their LAWYER id (distinct from their user id). */
    private UUID registerLawyerAndGetId(String email) throws Exception {
        registerAndLoginLawyer(email);

        User user = userRepositoryForSupport.findByEmail(email).orElseThrow();
        return lawyerRepository.findByUser(user).orElseThrow().getId();
    }

    private String verifyUrl(UUID lawyerId) {
        return "/api/admin/lawyers/" + lawyerId + "/verify";
    }

    // ------------------------------------------------------------ happy path

    @Test
    @DisplayName("verifying a lawyer returns 200 and marks them verified")
    void verifyReturns200AndTheLawyerIsMarkedVerified() throws Exception {
        UUID lawyerId = registerLawyerAndGetId(uniqueEmail("pending"));
        String adminToken = registerAndLoginAdmin(uniqueEmail("admin"));

        mockMvc.perform(put(verifyUrl(lawyerId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lawyerId.toString()))
                .andExpect(jsonPath("$.verified").value(true));

        // Confirmed against the database, not only the response.
        org.junit.jupiter.api.Assertions.assertTrue(
                lawyerRepository.findById(lawyerId).orElseThrow().getVerified(),
                "the lawyer should be persisted as verified");
    }

    /**
     * The actual regression guard.
     *
     * Reading `specializations` from the response body is what proves the LAZY
     * collection was initialised while a transaction was still open. Before the
     * fix this request produced 500 "An unexpected error occurred" - after the
     * write had already committed.
     */
    @Test
    @DisplayName("the verify response carries the full profile, including specializations")
    void verifyResponseCarriesTheFullProfileIncludingSpecializations() throws Exception {
        UUID lawyerId = registerLawyerAndGetId(uniqueEmail("pending"));
        String adminToken = registerAndLoginAdmin(uniqueEmail("admin"));

        mockMvc.perform(put(verifyUrl(lawyerId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())

                // The lazily-loaded collection - the field that used to throw.
                .andExpect(jsonPath("$.specializations").isArray())
                .andExpect(jsonPath("$.specializations[0]").value("Family Law"))

                // The rest of LawyerProfileResponse, so the contract is pinned.
                .andExpect(jsonPath("$.fullName").value("Test Lawyer"))
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.barCouncilNumber").exists())
                .andExpect(jsonPath("$.experienceYears").value(5))
                .andExpect(jsonPath("$.city").value("Mumbai"))
                .andExpect(jsonPath("$.officeAddress").value("123 Court Road"))
                .andExpect(jsonPath("$.totalReviews").value(0))

                /*
                 * Asserted as present rather than equal to a literal.
                 * `consultationFee` is a BigDecimal over numeric(10,2) and
                 * `rating` a double, so both deserialise as a numeric type whose
                 * equals() does not match an int literal - 1500.00 is not 1500.
                 * Their exact values are not what this test is about.
                 */
                .andExpect(jsonPath("$.consultationFee").exists())
                .andExpect(jsonPath("$.rating").exists());
    }

    /**
     * Pins the CURRENT contract: verification is idempotent.
     *
     * `verifyLawyer` assigns `verified = true` with no check on the existing
     * state, so a repeat call answers 200 rather than 409. Two things depend on
     * that and neither would fail loudly if it changed:
     *
     *   - `services/admin-lawyer-service.ts` documents it in prose
     *   - `VerificationActions` has no 409 branch, so a conflict would surface
     *     as the generic "Please try again" fallback
     *
     * It is also the behaviour most likely to change deliberately: replacing the
     * `verified` boolean with a VerificationStatus enum makes VERIFIED ->
     * VERIFIED a natural 409 candidate. This test turns that into a failing
     * assertion at the moment of the change rather than a surprise in manual
     * testing - which is how the LazyInitializationException above was found.
     *
     * The specializations assertion is repeated deliberately: the second call
     * takes the same path through toProfileResponse, so it is subject to the
     * same lazy-loading failure.
     */
    @Test
    @DisplayName("verifying an already-verified lawyer is idempotent")
    void verifyingAnAlreadyVerifiedLawyerIsIdempotent() throws Exception {
        UUID lawyerId = registerLawyerAndGetId(uniqueEmail("pending"));
        String adminToken = registerAndLoginAdmin(uniqueEmail("admin"));

        mockMvc.perform(put(verifyUrl(lawyerId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        // Second call against a lawyer who is already verified.
        mockMvc.perform(put(verifyUrl(lawyerId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lawyerId.toString()))
                .andExpect(jsonPath("$.verified").value(true))

                // Still the full LawyerProfileResponse, not a truncated body.
                .andExpect(jsonPath("$.specializations").isArray())
                .andExpect(jsonPath("$.specializations[0]").value("Family Law"))
                .andExpect(jsonPath("$.fullName").value("Test Lawyer"))
                .andExpect(jsonPath("$.barCouncilNumber").exists());

        org.junit.jupiter.api.Assertions.assertTrue(
                lawyerRepository.findById(lawyerId).orElseThrow().getVerified(),
                "the lawyer should remain verified after a repeat call");
    }

    @Test
    @DisplayName("a verified lawyer leaves the pending queue")
    void verifiedLawyerLeavesThePendingQueue() throws Exception {
        UUID lawyerId = registerLawyerAndGetId(uniqueEmail("pending"));
        String adminToken = registerAndLoginAdmin(uniqueEmail("admin"));

        /*
         * A JsonPath filter rather than a Hamcrest collection matcher: it reads
         * as "is this id present", and it is unaffected by the other unverified
         * lawyers this shared database accumulates.
         */
        String thisLawyer = "$.content[?(@.id == '" + lawyerId + "')]";

        mockMvc.perform(get(PENDING_ALL).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(thisLawyer).isNotEmpty());

        mockMvc.perform(put(verifyUrl(lawyerId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get(PENDING_ALL).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(thisLawyer).isEmpty());
    }

    // --------------------------------------------------------------- failure

    @Test
    @DisplayName("verifying an unknown lawyer returns 404")
    void verifyingUnknownLawyerReturns404() throws Exception {
        String adminToken = registerAndLoginAdmin(uniqueEmail("admin"));

        mockMvc.perform(put(verifyUrl(UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
}
