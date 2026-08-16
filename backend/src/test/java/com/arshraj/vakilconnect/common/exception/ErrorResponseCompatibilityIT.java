package com.arshraj.vakilconnect.common.exception;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backward compatibility for the new nullable `code` field on ErrorResponse.
 *
 * The risk this guards is specific and easy to miss: Jackson serialises nulls by
 * DEFAULT, so simply adding the field would have added `"code": null` to every
 * error body already in production - a contract change to handlers nobody
 * touched. `@JsonInclude(NON_NULL)` on that one field prevents it, and an
 * annotation nobody tests is an annotation somebody deletes.
 *
 * The `fieldErrors` assertion is the mirror image: it proves the annotation was
 * applied at FIELD level and not to the whole class, which would have silently
 * removed `"fieldErrors": null` from existing responses instead.
 */
@DisplayName("ErrorResponse backward compatibility")
class ErrorResponseCompatibilityIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("a validation error carries no `code` key at all")
    void validationErrorHasNoCode() throws Exception {
        Map<String, Object> body = clientRegistration(uniqueEmail("compat"));
        body.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                /*
                 * doesNotHaveJsonPath(), NOT doesNotExist().
                 *
                 * doesNotExist() is satisfied by a null VALUE as well as an
                 * absent key, so it would pass even if @JsonInclude were
                 * deleted - a test that cannot fail for the reason it exists.
                 * doesNotHaveJsonPath() asserts the KEY is absent, which is
                 * only true when NON_NULL is applied.
                 */
                .andExpect(jsonPath("$.code").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("a 404 keeps its original shape, including fieldErrors: null")
    void notFoundKeepsShape() throws Exception {
        mockMvc.perform(get("/api/lawyers/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").doesNotHaveJsonPath())
                /*
                 * hasJsonPath(), which is satisfied by a present-but-null value.
                 *
                 * This is the mirror-image guard: a 404 has no field errors, so
                 * `fieldErrors` serialises as null TODAY and must continue to.
                 * Had @JsonInclude been placed on the CLASS instead of the one
                 * field, this key would have disappeared - the same breakage
                 * the annotation was added to prevent, in the other direction.
                 */
                .andExpect(jsonPath("$.fieldErrors").hasJsonPath());
    }

    @Test
    @DisplayName("a duplicate registration still returns 409 with no code")
    void duplicateKeepsShape() throws Exception {
        Map<String, Object> first = clientRegistration(uniqueEmail("compatdup"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(first)))
                .andExpect(status().isCreated());

        Map<String, Object> second = new LinkedHashMap<>(first);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.message").value("Email already exists."));
    }
}
