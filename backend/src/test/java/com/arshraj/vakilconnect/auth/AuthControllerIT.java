package com.arshraj.vakilconnect.auth;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
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
