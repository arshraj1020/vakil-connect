package com.arshraj.vakilconnect.ai.analysis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds for one document analysis.
 *
 * SEPARATE FROM AiRetrievalProperties, for the reason every properties record
 * in this package is separate: analysis reads ONE document from end to end,
 * retrieval reads the best few passages from MANY. They want different budgets
 * and they change for different reasons, so sharing
 * `max-context-characters` between them would mean tuning retrieval quality
 * silently changed how much of a contract gets analysed.
 *
 * FOUR VALUES, AND THEY DIVIDE INTO TWO JOBS.
 *
 * `max-context-characters` bounds what goes INTO the model. The other three
 * bound what comes OUT of it - and that is not a formatting nicety. The model's
 * reply is untrusted text that becomes an HTTP response body; without a ceiling,
 * a model that loops (small local models do, especially on lists) would return
 * ten thousand "parties" and this service would faithfully serialise all of
 * them.
 *
 * NO CREDENTIAL COMPONENT, like every other properties record here. Analysis
 * reuses the existing LlmClient, so it introduces no provider and no key.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai.analysis")
public record AiAnalysisProperties(

        /*
         * Hard ceiling on document text placed in the prompt, in characters.
         *
         * 12000 characters is roughly 3000 tokens - larger than AI-3's 8000,
         * deliberately. Retrieval is allowed to be stingy because it only needs
         * the passages that answer one question; analysis is asked "who are the
         * parties, what are the dates, what are the obligations", which is a
         * whole-document question, so cutting it short costs real answers.
         *
         * It is still a ceiling rather than "everything", because a 10MB upload
         * chunks into thousands of passages and llama3.2's window is not
         * elastic. When the budget runs out the response says so - see
         * `truncated` on DocumentAnalysis - rather than quietly analysing the
         * first third and presenting it as the whole.
         *
         * CHARACTERS, NOT TOKENS. A token count needs a tokenizer matching
         * whichever model is configured, and becomes a lie the moment the model
         * changes.
         */
        @NotNull @Min(500)
        Integer maxContextCharacters,

        /*
         * Longest `summary` returned, in characters.
         *
         * A summary is prose, so this is generous; it exists to stop a
         * degenerate generation from becoming the response body.
         */
        @NotNull @Min(50)
        Integer maxSummaryCharacters,

        /*
         * Most entries kept in any one of the five lists.
         *
         * Excess entries are DROPPED, not rejected. A model that returns 40
         * obligations for a contract that has 12 has padded the tail, and
         * failing the whole analysis over padding would be worse for the user
         * than returning the first 20 - which the parser takes in the order the
         * model gave them, so the drop is deterministic.
         */
        @NotNull @Min(1)
        Integer maxListItems,

        /*
         * Longest single list entry, in characters.
         *
         * These are meant to be phrases - "Landlord: Ramesh Kumar", "Rent due
         * on the 5th of each month" - not paragraphs. An entry over the limit
         * is truncated rather than dropped, because a clipped obligation still
         * tells the user something and an absent one does not.
         */
        @NotNull @Min(20)
        Integer maxItemCharacters
) {
}
