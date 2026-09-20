package com.arshraj.vakilconnect.ai.compare;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds for one document comparison (AI-5).
 *
 * SEPARATE FROM AiAnalysisProperties, for the reason every properties record
 * in this package is separate: a comparison holds TWO documents in context at
 * once, so its per-document budget is deliberately smaller than a single
 * analysis gets - the total prompt is still bounded by what a local model can
 * usefully attend to.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai.comparison")
public record AiComparisonProperties(

        /*
         * Character ceiling applied to EACH document independently, not to
         * their sum. Bounding the sum would let one large document starve the
         * other's share; bounding each document the same way means a small
         * document is never cut just because its counterpart is huge.
         */
        @NotNull @Min(500)
        Integer maxContextCharactersPerDocument,

        @NotNull @Min(50)
        Integer maxSummaryCharacters,

        /** Most entries kept in any one of the three difference lists. */
        @NotNull @Min(1)
        Integer maxListItems,

        @NotNull @Min(20)
        Integer maxItemCharacters
) {
}
