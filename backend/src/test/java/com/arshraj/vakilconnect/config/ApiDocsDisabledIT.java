package com.arshraj.vakilconnect.config;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit C1 regression: with springdoc's two switches off — exactly what
 * application-prod.yaml sets — the documentation endpoints must not exist.
 *
 * 404, not 401/403, is the correct expectation and the stronger one: the
 * permitAll matchers for these paths are still present in SecurityConfig, so a
 * request sails through authorization and must find NO HANDLER behind it. If
 * springdoc ever started registering handlers despite the flags (a behaviour
 * change in the library), authorization would admit the request, this test
 * would see a 200 and fail — which is precisely the regression it exists to
 * catch. A 401 expectation would pass in that broken world only if the
 * matchers were also removed, silently coupling two independent decisions.
 *
 * NOTE ON COST. The @TestPropertySource forks a second application context, so
 * the suite pays one extra context start-up for this class. Accepted: the
 * alternative is no automated proof that the production posture actually
 * disables the endpoints.
 *
 * The default-on behaviour needs no companion test here: springdoc registers
 * its endpoints in every other IT's context, and OpenApiConfig failing to load
 * would break none of them — but the enabled path is the developer-visible one
 * and regressions there are noticed within minutes, not by an attacker.
 */
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@DisplayName("API docs disabled (production posture)")
class ApiDocsDisabledIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("the OpenAPI spec endpoint does not exist")
    void apiDocsNotFound() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("grouped and versioned spec paths do not exist either")
    void apiDocsSubPathsNotFound() throws Exception {
        // The permitAll matcher is /v3/api-docs/** — assert deeper paths too,
        // not just the root, so a partially-registered handler cannot hide.
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the Swagger UI entry point does not exist")
    void swaggerUiHtmlNotFound() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the Swagger UI asset path does not exist")
    void swaggerUiAssetsNotFound() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("disabling the docs does not disturb a real public endpoint")
    void apiStillWorks() throws Exception {
        // Guards against the failure mode where the conditional on
        // OpenApiConfig (or a future refactor of it) takes unrelated
        // configuration down with it in the prod posture.
        mockMvc.perform(get("/api/lawyers"))
                .andExpect(status().isOk());
    }
}
