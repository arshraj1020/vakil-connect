package com.arshraj.vakilconnect.ai.embedding;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which embedding model to use, and how wide its output is.
 *
 * SEPARATE FROM AiProperties, for the reason AiDocumentProperties is: chat and
 * embedding are different models with different lifetimes, and
 * AiPropertiesTest.componentSetIsPinned() exists so nothing is added to
 * AiProperties without a human deciding whether it holds a secret. Reusing it
 * to avoid a second class would mean answering that review gate with a shrug.
 *
 * Reuses {@code vakilconnect.ai.base-url} rather than declaring its own: the
 * embedding endpoint is on the SAME Ollama server as the chat endpoint, and two
 * properties for one address would be two things to keep in step.
 *
 * NO CREDENTIAL COMPONENT, and there cannot be one - Ollama authenticates
 * nothing. The zero-cost requirement holds through AI-2 unchanged.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai.embedding")
public record AiEmbeddingProperties(

        /** `stub` or `ollama`. Mirrors vakilconnect.ai.provider exactly. */
        @NotBlank
        String provider,

        /*
         * The Ollama embedding model tag.
         *
         * nomic-embed-text BY DEFAULT, and the choice is worth stating:
         *
         *   - 768 dimensions, ~274MB on disk. Small enough to pull in under a
         *     minute and to run on a laptop with no GPU, which is the binding
         *     constraint for a project whose whole premise is zero cost.
         *   - Trained specifically for RETRIEVAL, with distinct prefixes for
         *     documents and queries. That matters more than raw benchmark
         *     scores here: the task is finding the right clause, not scoring
         *     well on sentence similarity.
         *   - Long context (8192 tokens), comfortably more than any chunk this
         *     pipeline produces, so no chunk is silently truncated mid-clause.
         *
         * mxbai-embed-large (1024 dims, ~670MB) scores better and is the
         * upgrade if retrieval quality disappoints. It is not the default
         * because it is 2.5x the download and the dimension change is a
         * migration - see below.
         */
        @NotBlank
        String model,

        /*
         * MUST EQUAL THE `vector(n)` WIDTH IN V9, AND A TEST ENFORCES THAT.
         *
         * SQL DDL takes no variables, so the width is a literal in the
         * migration and this is the second declaration of the same fact.
         * AiDocumentChunkSchemaIT reads the column's real typmod from
         * pg_attribute and asserts it matches this value, so the two cannot
         * drift silently.
         *
         * Configurable NOT so it can be changed casually, but so the check
         * above has something to compare against and so a deliberate model
         * change has one obvious place to start. Changing it alone will fail
         * the build - correctly, because a new dimension needs an ALTER COLUMN
         * and a full re-ingest.
         */
        @NotNull
        @Min(1)
        Integer dimension
) {

    public static final String STUB = "stub";
    public static final String OLLAMA = "ollama";
}
