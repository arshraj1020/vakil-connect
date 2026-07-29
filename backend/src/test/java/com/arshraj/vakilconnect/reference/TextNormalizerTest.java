package com.arshraj.vakilconnect.reference;

import com.arshraj.vakilconnect.common.util.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Normalized-name generation.
 *
 * A plain unit test - no Spring context, no container. The rule is pure and
 * this is the one place it is specified, so it should run in milliseconds and
 * fail loudly if the algorithm drifts from the values seeded by V3.
 */
@DisplayName("Text normalization")
class TextNormalizerTest {

    @Test
    @DisplayName("lowercases, trims and collapses internal whitespace")
    void collapsesWhitespaceAndLowercases() {
        assertEquals("new delhi", TextNormalizer.normalize("  New   Delhi "));
        assertEquals("mumbai", TextNormalizer.normalize("MUMBAI"));
        assertEquals("vasco da gama", TextNormalizer.normalize("Vasco  da Gama"));
    }

    @Test
    @DisplayName("strips diacritics so accented input matches plain seed data")
    void stripsDiacritics() {
        assertEquals("puducherry", TextNormalizer.normalize("Puduchérry"));
        assertEquals("belagavi", TextNormalizer.normalize("Belagāvi"));
    }

    @Test
    @DisplayName("is idempotent - normalising an already-normalised value is a no-op")
    void isIdempotent() {
        String once = TextNormalizer.normalize("  Chhatrapati  Sambhajinagar ");
        assertEquals(once, TextNormalizer.normalize(once));
        assertEquals("chhatrapati sambhajinagar", once);
    }

    @Test
    @DisplayName("null in, null out")
    void handlesNull() {
        assertNull(TextNormalizer.normalize(null));
    }

    @Test
    @DisplayName("empty and whitespace-only input normalise to empty")
    void handlesBlank() {
        assertEquals("", TextNormalizer.normalize(""));
        assertEquals("", TextNormalizer.normalize("   "));
    }

    @Test
    @DisplayName("matches() compares on the normalised form")
    void matchesComparesNormalised() {
        assertTrue(TextNormalizer.matches("Mumbai", "  mumbai "));
        assertTrue(TextNormalizer.matches("Puducherry", "Puduchérry"));
        assertFalse(TextNormalizer.matches("Mumbai", "Mumbi"));
        assertFalse(TextNormalizer.matches(null, "Mumbai"));
    }

    /**
     * Punctuation is deliberately preserved - the rule stays simple enough to
     * reproduce exactly in a SQL seed. This pins that decision so a future
     * "improvement" that strips hyphens has to be a conscious change.
     */
    @Test
    @DisplayName("punctuation is preserved, not stripped")
    void preservesPunctuation() {
        assertEquals("vasco-da-gama", TextNormalizer.normalize("Vasco-da-Gama"));
    }
}
