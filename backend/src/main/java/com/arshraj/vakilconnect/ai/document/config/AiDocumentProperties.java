package com.arshraj.vakilconnect.ai.document.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Limits on document uploads.
 *
 * SEPARATE FROM AiProperties, DELIBERATELY. The obvious move is to add a
 * `maxFileSize` component to the existing record and be done. Two reasons not
 * to:
 *
 *   1. They are different concerns with different lifetimes. AiProperties
 *      configures which model answers and how; this configures what a user may
 *      upload. A change to one has nothing to say about the other, and merging
 *      them means every future document setting widens the surface of the
 *      class the LLM adapters depend on.
 *
 *   2. AiPropertiesTest.componentSetIsPinned() would fail - by design. That
 *      test exists so no component is added to AiProperties without a human
 *      deciding whether it holds a secret. Adding an unrelated field just to
 *      avoid a second properties class would mean answering a security review
 *      gate with a shrug.
 *
 * Bound the same way as AiProperties, IdentityProperties and EmailProperties: a
 * validated record, registered explicitly on the application class, with the
 * DEFAULT LIVING IN application.yaml as ${ENV_VAR:default} rather than as
 * {@code @DefaultValue} here - one visible, greppable, environment-overridable
 * source per key.
 *
 * NO CREDENTIAL COMPONENT, and there is nothing here that could become one -
 * but the same rule applies as to AiProperties: a record prints every component
 * from its generated toString(), so anything secret added later needs a
 * redacting override first.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai.document")
public record AiDocumentProperties(

        /*
         * The largest file a user may upload.
         *
         * DataSize, not long. Spring binds "10MB" and "512KB" natively, so the
         * yaml and the environment variable say what they mean instead of
         * carrying a number of bytes that nobody can read at a glance and
         * everyone gets wrong by a factor of 1024.
         *
         * MUST STAY BELOW spring.servlet.multipart.max-file-size. Spring's
         * container rejects an oversized part before any controller method
         * runs, so if this value were the larger of the two the application's
         * own check would be unreachable and every oversize would arrive as a
         * MaxUploadSizeExceededException instead. Both paths are mapped to the
         * same 413 + DOCUMENT_TOO_LARGE precisely because that ordering is easy
         * to get wrong, but the intended path is this one.
         *
         * The number matters more here than it would elsewhere: document bytes
         * live in PostgreSQL on a free-tier plan with a storage cap, so this
         * ceiling is what keeps the deployment viable.
         */
        @NotNull
        DataSize maxFileSize
) {

    /** Convenience for comparisons against a byte count. */
    public long maxFileSizeBytes() {
        return maxFileSize.toBytes();
    }
}
