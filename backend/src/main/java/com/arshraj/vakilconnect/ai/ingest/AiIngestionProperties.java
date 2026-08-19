package com.arshraj.vakilconnect.ai.ingest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How a document is split before embedding.
 *
 * THE DEFAULTS ARE CHOSEN FOR LEGAL PROSE, not copied from a tutorial.
 *
 * chunk-size 1200 CHARACTERS (roughly 250-300 tokens). Legal documents are
 * organised in clauses, and a clause plus its sub-points usually fits in that
 * span. Smaller chunks (the common 500-character default) routinely cut a
 * clause off from its own conditions, so retrieval returns "the tenant shall
 * pay" without "unless clause 7 applies" - which for legal text is worse than
 * returning nothing. Much larger chunks dilute the embedding: one vector
 * covering four unrelated clauses is close to all of them and precise about
 * none.
 *
 * chunk-overlap 200 CHARACTERS, about a sixth of a chunk. Overlap exists so a
 * definition or condition sitting on a boundary appears in BOTH neighbours and
 * cannot be lost by an unlucky split. Legal text refers backwards constantly
 * ("such notice", "the said premises"), so the antecedent matters. More overlap
 * would mostly duplicate storage and embedding time for diminishing benefit.
 *
 * max-chunks-per-document is a COST CEILING, not a quality setting. Every chunk
 * is one local inference call; without a ceiling a maximum-size upload could
 * occupy the machine for many minutes. 2000 chunks is far above any realistic
 * contract and far below anything that would hang a laptop.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai.ingestion")
public record AiIngestionProperties(

        @NotNull @Min(100)
        Integer chunkSize,

        @NotNull @Min(0)
        Integer chunkOverlap,

        @NotNull @Min(1)
        Integer maxChunksPerDocument
) {

    /**
     * Overlap must be smaller than the chunk, or splitting cannot make
     * progress - each chunk would re-emit most of its predecessor and the
     * document would never terminate. Caught at BIND time so it is a startup
     * failure naming the property, not an infinite loop during ingestion.
     */
    public AiIngestionProperties {
        if (chunkSize != null && chunkOverlap != null && chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "vakilconnect.ai.ingestion.chunk-overlap (" + chunkOverlap
                            + ") must be smaller than chunk-size (" + chunkSize + ")");
        }
    }
}
