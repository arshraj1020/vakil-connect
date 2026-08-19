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
import org.testcontainers.utility.DockerImageName;

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

    /**
     * PGVECTOR'S OFFICIAL IMAGE, NOT postgres:16-alpine (AI-2).
     *
     * pgvector is a compiled C extension: it has to be present in the server
     * image, and `CREATE EXTENSION vector` in V9 fails outright without it.
     * There is no runtime install and no pure-SQL workaround.
     *
     * SAME POSTGRES MAJOR VERSION, so nothing about the existing suite changes.
     * `pgvector/pgvector:pg16` is the upstream image built on the official
     * postgres:16 - Debian-based rather than Alpine, which is the only visible
     * difference and affects nothing this project relies on.
     *
     * COSTS A ONE-TIME ~150MB DOCKER PULL on each machine and in CI. Every
     * integration test in the suite inherits this container, so the change is
     * all-or-nothing; running a second container just for the AI tests was
     * considered and rejected, because two containers per build and two schema
     * paths is a worse trade than one larger image.
     *
     * DOES NOT USE DockerImageName.asCompatibleSubstituteFor. Testcontainers
     * matches PostgreSQLContainer against the image name, and this image is not
     * called `postgres` - but it declares itself compatible via the standard
     * substitute mechanism below, which is the supported way to say "this is
     * still PostgreSQL" rather than disabling the check.
     */
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE);

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

    /**
     * Unique per TEST, not per call - two calls with the same prefix inside one
     * test return the SAME address. Callers that register more than one account
     * must use {@link #distinctEmail(String)} instead.
     */
    protected String uniqueEmail(String prefix) {
        return prefix + "_" + uniqueSuffix + "@test.com";
    }

    /**
     * Unique per CALL, for tests that register several accounts.
     *
     * `users.email` is UNIQUE, so a second registration reusing the address
     * returns 409 and creates nothing. That failure is quiet: register() does
     * not assert a status, and a seed helper that then looks the lawyer up by
     * email finds the FIRST one - so the test proceeds with two references to a
     * single row and fails later somewhere unrelated. This is the same hazard
     * uniqueBarCouncilNumber() already guards against for the other unique
     * column.
     */
    protected String distinctEmail(String prefix) {
        return prefix + "_" + uniqueSuffix + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
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
        return lawyerRegistration(email, "Mumbai");
    }

    /**
     * As above, with an explicit city.
     *
     * The city is the one field whose value changes what the reference
     * dual-write and the Phase 2G read cut-over do with the row, so tests that
     * exercise those paths need to choose it - canonical, alias, or
     * unresolvable free text.
     */
    protected Map<String, Object> lawyerRegistration(String email, String city) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("barCouncilNumber", uniqueBarCouncilNumber());
        profile.put("experienceYears", 5);
        profile.put("bio", "Experienced advocate");
        profile.put("consultationFee", 1500);
        profile.put("city", city);
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
        admin.setEmailVerified(true);
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
