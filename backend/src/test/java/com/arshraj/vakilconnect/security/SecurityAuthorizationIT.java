package com.arshraj.vakilconnect.security;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the role matrix: anonymous / CLIENT / LAWYER / ADMIN against the
 * public, client, lawyer and admin endpoint groups.
 */
@DisplayName("Role-based authorization")
class SecurityAuthorizationIT extends AbstractIntegrationTest {

    private static final String CLIENT_ENDPOINT = "/api/client/dashboard";
    private static final String LAWYER_ENDPOINT = "/api/lawyer/dashboard";
    private static final String ADMIN_ENDPOINT = "/api/admin/dashboard";
    private static final String AUTHENTICATED_ENDPOINT = "/api/users/me";

    @Autowired
    private UserRepository userRepository;

    // registerAndLoginAdmin(...) lives in AbstractIntegrationTest.

    // ------------------------------------------------------------- anonymous

    @Test
    @DisplayName("anonymous can browse lawyers (public)")
    void anonymousCanSearchLawyers() throws Exception {
        mockMvc.perform(get("/api/lawyers"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous gets 401 on every protected endpoint")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CLIENT_ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(LAWYER_ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_ENDPOINT)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a malformed or unsigned token is rejected with 401")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer("not-a-real-jwt")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- client

    @Test
    @DisplayName("CLIENT reaches client endpoints but is forbidden from lawyer/admin")
    void clientRoleMatrix() throws Exception {
        String token = registerAndLoginClient(uniqueEmail("client"));

        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get(CLIENT_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get(LAWYER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- lawyer

    @Test
    @DisplayName("LAWYER reaches lawyer endpoints but is forbidden from client/admin")
    void lawyerRoleMatrix() throws Exception {
        String token = registerAndLoginLawyer(uniqueEmail("lawyer"));

        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get(LAWYER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get(CLIENT_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------- admin

    @Test
    @DisplayName("ADMIN reaches admin endpoints but is forbidden from client/lawyer")
    void adminRoleMatrix() throws Exception {
        String token = registerAndLoginAdmin(uniqueEmail("admin"));

        mockMvc.perform(get(ADMIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get(CLIENT_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(LAWYER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------ deactivated user

    @Test
    @DisplayName("a deactivated user can no longer log in")
    void deactivatedUserCannotLogIn() throws Exception {
        String email = uniqueEmail("deactivated");
        registerAndLoginClient(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get(CLIENT_ENDPOINT)).andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> login(email, DEFAULT_PASSWORD));
    }

    /**
     * Regression test for the JWT filter bypassing the disabled flag.
     *
     * `CustomUserDetailsService` builds the principal with
     * `.disabled(!user.isActive())`, but that flag is only enforced by
     * `DaoAuthenticationProvider`, which runs at login. `JwtAuthenticationFilter`
     * authenticates straight from the UserDetails, so before the fix a
     * deactivated account kept full API access until its token expired - up to
     * 24 hours of access an administrator believed they had revoked.
     *
     * The test asserts the token WORKS first. Without that step a 401 at the end
     * would prove nothing: a token that was never valid would pass just as
     * happily, and the test would still be green if the filter were reverted to
     * rejecting everything.
     */
    @Test
    @DisplayName("a deactivated user's existing token is rejected with 401 on the next request")
    void deactivatedUserTokenIsRejectedImmediately() throws Exception {
        String email = uniqueEmail("revoked");
        String token = registerAndLoginClient(email);

        // Precondition: this exact token is accepted while the account is active.
        mockMvc.perform(get(CLIENT_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        /*
         * Same token, no re-login. Deactivation must take effect on the very
         * next request, and the response must be the JSON envelope from
         * RestAuthenticationEntryPoint rather than a container error page - so
         * the assertions cover the whole lifecycle, not just the status line.
         */
        mockMvc.perform(get(CLIENT_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(CLIENT_ENDPOINT));

        // Not just the role-scoped route: every authenticated endpoint is closed.
        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------- deleted user

    /**
     * Regression test for an uncaught exception inside the JWT filter.
     *
     * A well-formed token whose subject no longer exists makes
     * `loadUserByUsername` throw `UsernameNotFoundException`. That is an
     * `AuthenticationException`, NOT a `JwtException`, so it escaped the filter's
     * catch clause and surfaced as 500 - leaking an internal failure for what is
     * simply an unusable credential.
     *
     * A freshly registered CLIENT owns no lawyer, appointment or review rows, so
     * the delete touches no foreign key.
     */
    @Test
    @DisplayName("a deleted user's existing token is rejected with 401, not 500")
    void deletedUserTokenIsUnauthorizedNotServerError() throws Exception {
        String email = uniqueEmail("deleted");
        String token = registerAndLoginClient(email);

        // Precondition: the token is genuinely valid before the account is removed.
        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        userRepository.delete(user);

        /*
         * `isUnauthorized()` already excludes 500, but the body assertions are
         * what distinguish a handled 401 from a servlet error page that happened
         * to carry the right status.
         */
        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get(CLIENT_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }
}
