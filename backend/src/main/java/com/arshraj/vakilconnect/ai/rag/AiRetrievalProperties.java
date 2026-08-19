package com.arshraj.vakilconnect.ai.rag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How much context retrieval is allowed to gather.
 *
 * Separate from AiProperties and AiEmbeddingProperties for the reason each of
 * those is separate: different concern, different lifetime, and
 * AiPropertiesTest pins AiProperties' component set so nothing lands there
 * without a security review.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.ai.retrieval")
public record AiRetrievalProperties(

        /*
         * How many chunks the vector search returns at most.
         *
         * 6 is a deliberate middle. Too few and a question spanning two clauses
         * gets half an answer; too many and the prompt fills with weakly-related
         * passages that dilute the model's attention and slow local inference -
         * every extra chunk is more tokens for a CPU to process.
         */
        @NotNull @Min(1)
        Integer topK,

        /*
         * The COSINE DISTANCE ceiling. Lower is more similar: 0 is identical
         * direction, 1 is orthogonal, 2 is opposite.
         *
         * 0.6 (similarity 0.4) is deliberately permissive rather than strict.
         * Being too strict is the worse failure here: the user gets
         * "insufficient evidence" for a question their document does answer,
         * which reads as the feature being broken. Being slightly loose costs a
         * weak passage in the context, which the grounded prompt tells the model
         * to ignore.
         *
         * THIS IS THE ONE VALUE MOST WORTH TUNING against a real corpus once
         * AI-4 has evaluation data. It is a guess informed by how
         * nomic-embed-text scores, not a measurement.
         */
        @NotNull @DecimalMin("0.0") @DecimalMax("2.0")
        Double maxDistance,

        /*
         * Hard ceiling on prompt context, in characters.
         *
         * 8000 characters is roughly 2000 tokens - comfortably inside a small
         * local model's window with room for the system prompt, the question
         * and the answer. Without it, topK x chunkSize could reach 7200
         * characters of context alone, and a larger topK later would silently
         * push past what the model can attend to.
         *
         * Characters, not tokens: a token count needs a tokenizer matching
         * whichever model is configured, and would become a lie the moment the
         * model changed.
         */
        @NotNull @Min(500)
        Integer maxContextCharacters,

        /*
         * Longest question accepted.
         *
         * A bound, not a feature. The question is embedded and then placed in
         * the prompt, so an unbounded one is both an inference cost and a way to
         * push the system instructions out of a small context window - which is
         * a prompt-injection vector, not merely a performance concern.
         */
        @NotNull @Min(10)
        Integer maxQuestionCharacters
) {
}
