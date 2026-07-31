package com.arshraj.vakilconnect.auth;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Registration and login")
class AuthControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("registers a client and returns 201 with the CLIENT role")
    void registersClient() throws Exception {
        String email = uniqueEmail("client");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("registers a lawyer atomically and returns 201 with the LAWYER role")
    void registersLawyerWithProfile() throws Exception {
        String email = uniqueEmail("lawyer");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(lawyerRegistration(email))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("LAWYER"));
    }

    @Test
    @DisplayName("rejects a lawyer registration with no lawyerProfile (400)")
    void rejectsLawyerWithoutProfile() throws Exception {
        Map<String, Object> body = clientRegistration(uniqueEmail("nolawyerprofile"));
        body.put("role", "LAWYER");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects ADMIN via public registration (400)")
    void rejectsAdminRegistration() throws Exception {
        Map<String, Object> body = clientRegistration(uniqueEmail("admin"));
        body.put("role", "ADMIN");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects a duplicate email (409)")
    void rejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail("dup");
        register(clientRegistration(email));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rejects a password shorter than 8 characters (400)")
    void rejectsWeakPassword() throws Exception {
        Map<String, Object> body = clientRegistration(uniqueEmail("weak"));
        body.put("password", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("rejects a malformed email (400)")
    void rejectsInvalidEmail() throws Exception {
        Map<String, Object> body = clientRegistration(uniqueEmail("bad"));
        body.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("rejects missing required fields (400)")
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects malformed JSON (400)")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("logs in with valid credentials and returns a token")
    void loginSucceeds() throws Exception {
        String email = uniqueEmail("login");
        register(clientRegistration(email));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("logs in when the email case differs from registration")
    void loginIsCaseInsensitive() throws Exception {
        /*
         * Regression test for the Phase 0 defect.
         *
         * User.setEmail lowercases on write, so the row is stored lowercase no
         * matter what was submitted. login() used to hand the RAW request value
         * to authenticate(), so the lookup behind DaoAuthenticationProvider
         * missed the row entirely and a correct password came back 401.
         *
         * Case only, no surrounding whitespace: @Email on LoginRequest rejects a
         * padded address at the DTO boundary with a 400, so the trim half of
         * normalizeEmail is unreachable through this endpoint. It is kept in the
         * normaliser for internal callers, not exercised here - a test that sent
         * "  foo@bar.com  " would be asserting on Hibernate Validator, not on
         * the bug being fixed.
         */
        String email = uniqueEmail("mixedcase");

        // Asserted, not fire-and-forget: register() returns the MvcResult without
        // checking it, so a quiet 409 here would surface as a 401 below and be
        // misread as the normalisation still being broken.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());

        Map<String, Object> body = new LinkedHashMap<>();
        // Locale.ROOT so the test asserts the same thing on a Turkish-default JVM
        // as it does on an English one - the exact hazard the normaliser pins.
        body.put("email", email.toUpperCase(Locale.ROOT));
        body.put("password", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                // The response carries the stored (normalised) address, not the
                // shouty one that was submitted.
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("rejects login with a wrong password (401)")
    void loginWithWrongPasswordFails() throws Exception {
        String email = uniqueEmail("wrongpass");
        register(clientRegistration(email));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", "totally-wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects login for an unknown email (401)")
    void loginWithUnknownEmailFails() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", uniqueEmail("ghost"));
        body.put("password", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isUnauthorized());
    }
}
