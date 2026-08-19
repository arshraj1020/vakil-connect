package com.arshraj.vakilconnect.ai.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Normalization, which must clean parser noise WITHOUT rewriting the document.
 *
 * Every test here is really the same assertion from a different angle: the
 * legal meaning survives. A normalizer tuned for ordinary prose - lowercase,
 * strip punctuation, collapse everything - would pass a "did it clean up" test
 * and destroy a contract.
 */
@DisplayName("DocumentTextNormalizer")
class DocumentTextNormalizerTest {

    private final DocumentTextNormalizer normalizer = new DocumentTextNormalizer();

    @Test
    @DisplayName("CRLF becomes LF")
    void normalisesCrlf() {
        assertEquals("line one\nline two", normalizer.normalize("line one\r\nline two"));
    }

    @Test
    @DisplayName("a bare CR becomes LF")
    void normalisesBareCr() {
        // Some PDF extractions emit CR alone. Three representations of one break
        // would put arbitrary differences into chunk hashes.
        assertEquals("line one\nline two", normalizer.normalize("line one\rline two"));
    }

    @Test
    @DisplayName("PARAGRAPH BOUNDARIES SURVIVE - the chunker splits on them")
    void preservesParagraphBoundaries() {
        String result = normalizer.normalize("Clause 1.\n\nClause 2.");

        assertTrue(result.contains("\n\n"),
                "a blank line separates clauses; collapsing it would destroy the "
                        + "structure recursive chunking depends on");
    }

    @Test
    @DisplayName("excess blank lines collapse to exactly one, page breaks included")
    void collapsesExcessBlankLines() {
        // A PDF page break can emit a dozen empty lines. One is as informative
        // as twelve; zero would merge two clauses.
        assertEquals("A\n\nB", normalizer.normalize("A\n\n\n\n\n\nB"));
    }

    @Test
    @DisplayName("clause numbering is untouched")
    void preservesClauseNumbering() {
        String result = normalizer.normalize(IngestionFixtures.legalText());

        // "7.2(a)" style references are addresses other clauses point at.
        assertTrue(result.contains("1.1"));
        assertTrue(result.contains("2.2"));
        assertTrue(result.contains("clause 5.3"));
    }

    @Test
    @DisplayName("punctuation that carries legal structure is untouched")
    void preservesPunctuation() {
        String result = normalizer.normalize(IngestionFixtures.legalText());

        // A semicolon separates enumerated conditions; a full stop ends an
        // obligation. Both are structure, not decoration.
        assertTrue(result.contains("; time being of the essence."));
        assertTrue(result.contains("\"Premises\""));
        assertTrue(result.contains("1.5%"));
        assertTrue(result.contains("Rs. 45,000"));
    }

    @Test
    @DisplayName("headings and case survive")
    void preservesHeadingsAndCase() {
        String result = normalizer.normalize(IngestionFixtures.legalText());

        assertTrue(result.contains("RESIDENTIAL TENANCY AGREEMENT"));
        assertTrue(result.contains("SECURITY DEPOSIT"));
        // "shall" and "may" are different obligations; case can distinguish a
        // Defined Term from an ordinary word.
        assertTrue(result.contains("The Tenant shall pay"));
    }

    @Test
    @DisplayName("no legal wording is removed")
    void doesNotRemoveWording() {
        String result = normalizer.normalize(IngestionFixtures.legalText());

        for (String phrase : new String[]{
                "interest-free deposit", "vacant possession",
                "attributable to the Landlord", "save where expressly stated" }) {
            // The last one is only in longLegalText; check the ones present.
            if (IngestionFixtures.legalText().contains(phrase)) {
                assertTrue(result.contains(phrase), "lost: " + phrase);
            }
        }
    }

    @Test
    @DisplayName("soft hyphens are removed, which REPAIRS words rather than altering them")
    void removesSoftHyphens() {
        // PDF extraction scatters U+00AD mid-word: "agree-ment" is "agreement".
        assertEquals("agreement", normalizer.normalize("agree­ment"));
    }

    @Test
    @DisplayName("zero-width characters and the BOM are removed")
    void removesInvisibleCharacters() {
        String result = normalizer.normalize("﻿The​Tenant");

        assertFalse(result.contains("﻿"));
        assertFalse(result.contains("​"));
    }

    @Test
    @DisplayName("non-breaking spaces become ordinary spaces")
    void normalisesExoticSpaces() {
        // Otherwise "Rs. 50,000" and "Rs. 50,000" hash and embed differently
        // for a difference no reader can see.
        assertEquals("Rs. 50,000", normalizer.normalize("Rs. 50,000"));
    }

    @Test
    @DisplayName("runs of spaces collapse but newlines do not")
    void collapsesHorizontalWhitespaceOnly() {
        // PDF column layout pads with long runs of spaces that mean nothing.
        assertEquals("A B\nC", normalizer.normalize("A     B\nC"));
    }

    @Test
    @DisplayName("DETERMINISTIC - the same input always gives the same output")
    void isDeterministic() {
        // Chunk boundaries and content hashes depend on this. A normalizer that
        // varied would make reprocessing produce different chunks for an
        // unchanged document.
        String input = IngestionFixtures.legalText();

        assertEquals(normalizer.normalize(input), normalizer.normalize(input));
        // Idempotent too: normalizing an already-normalized document is a no-op.
        assertEquals(normalizer.normalize(input),
                normalizer.normalize(normalizer.normalize(input)));
    }

    @Test
    @DisplayName("null and blank are safe")
    void handlesEmptyInput() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize("   \n\n  "));
    }
}
