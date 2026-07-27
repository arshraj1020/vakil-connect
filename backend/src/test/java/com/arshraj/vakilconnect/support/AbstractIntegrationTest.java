package com.arshraj.vakilconnect.support;

import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for integration tests.
 *
 * Uses a single PostgreSQL container shared by every test class in the JVM
 * (started once in the static initializer, torn down by Testcontainers' Ryuk on
 * exit). Flyway applies V1/V2 to it, and Hibernate runs with ddl-auto=validate,
 * so the tests exercise the real production schema.
 *
 * Requires Docker to be running locally.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "password123";

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepositoryForSupport;

    @Autowired
    protected PasswordEncoder passwordEncoderForSupport;

    private String uniqueSuffix;

    @BeforeEach
    void initUniqueSuffix() {
        // Every test uses fresh identifiers so tests never collide on the
        // unique email / bar council number constraints, and no cleanup is needed.
        this.uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------------------------------------------------------------- helpers

    protected String uniqueEmail(String prefix) {
        return prefix + "_" + uniqueSuffix + "@test.com";
    }

    /**
     * Unique per call, not merely per test: bar_council_number is UNIQUE, so a
     * test that registers two lawyers would otherwise fail its second
     * registration with a 409.
     */
    protected String uniqueBarCouncilNumber() {
        return "BC_" + uniqueSuffix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Request body for a CLIENT registration. */
    protected Map<String, Object> clientRegistration(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Test Client");
        body.put("email", email);
        body.put("password", DEFAULT_PASSWORD);
        body.put("phoneNumber", "9876543210");
        body.put("role", "CLIENT");
        return body;
    }

    /** Request body for a LAWYER registration, including the nested profile. */
    protected Map<String, Object> lawyerRegistration(String email) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("barCouncilNumber", uniqueBarCouncilNumber());
        profile.put("experienceYears", 5);
        profile.put("bio", "Experienced advocate");
        profile.put("consultationFee", 1500);
        profile.put("city", "Mumbai");
        profile.put("officeAddress", "123 Court Road");
        profile.put("specializations", List.of("Family Law"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Test Lawyer");
        body.put("email", email);
        body.put("password", DEFAULT_PASSWORD);
        body.put("phoneNumber", "9876543211");
        body.put("role", "LAWYER");
        body.put("lawyerProfile", profile);
        return body;
    }

    protected MvcResult register(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /** Registers a CLIENT and returns their JWT. */
    protected String registerAndLoginClient(String email) throws Exception {
        register(clientRegistration(email));
        return login(email, DEFAULT_PASSWORD);
    }

    /** Registers a LAWYER (with profile) and returns their JWT. */
    protected String registerAndLoginLawyer(String email) throws Exception {
        register(lawyerRegistration(email));
        return login(email, DEFAULT_PASSWORD);
    }

    /**
     * ADMIN cannot be created through the public API by design, so the row is
     * inserted directly and then authenticated normally.
     */
    protected String registerAndLoginAdmin(String email) throws Exception {
        User admin = new User();
        admin.setFullName("Test Admin");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoderForSupport.encode(DEFAULT_PASSWORD));
        admin.setPhoneNumber("9876543212");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setEnabled(true);
        userRepositoryForSupport.save(admin);

        return login(email, DEFAULT_PASSWORD);
    }

    /** Logs in and extracts the token from the response. */
    protected String login(String email, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
