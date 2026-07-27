package com.arshraj.vakilconnect.security;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
