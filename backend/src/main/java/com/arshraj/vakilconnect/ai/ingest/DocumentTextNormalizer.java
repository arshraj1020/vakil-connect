package com.arshraj.vakilconnect.ai.ingest;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Cleans parser noise out of extracted text without rewriting the document.
 *
 * THE GOVERNING RULE: THIS MUST NEVER CHANGE WHAT THE DOCUMENT SAYS.
 *
 * A normalizer for ordinary prose can afford to be aggressive - lowercase,
 * strip punctuation, collapse everything. Every one of those is WRONG here. In
 * a legal document:
 *
 *   - "shall" and "may" are different obligations, and case can distinguish a
 *     Defined Term from an ordinary word;
 *   - a semicolon separates enumerated conditions and a full stop ends an
 *     obligation, so punctuation is structure;
 *   - "7.2(a)" is an address that other clauses point at, and mangling it
 *     breaks every cross-reference in the document;
 *   - a blank line is usually a clause boundary, which is exactly what the
 *     chunker splits on.
 *
 * So this class removes only things that are unambiguously PARSER ARTEFACTS and
 * carry no meaning. Anything it is not certain about, it leaves alone. Every
 * transformation below is reversible in meaning even if not in bytes.
 *
 * DETERMINISTIC AND PURE: same input, same output, no configuration, no state.
 * Chunk boundaries and content hashes depend on it, so a normalizer that varied
 * would make reprocessing produce different chunks for an unchanged document.
 */
@Component
public class DocumentTextNormalizer {

    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        /*
         * 1. Unicode NFC.
         *
         * PDF extraction commonly yields decomposed forms - "é" as e + U+0301.
         * Two spellings of one word would hash differently and embed
         * differently, so composing first makes everything downstream stable.
         * NFC, not NFD or NFKC: NFKC would rewrite ligatures and typographic
         * characters, which changes the document's appearance and, for things
         * like "№" or fraction glyphs, its content.
         */
        String result = Normalizer.normalize(text, Normalizer.Form.NFC);

        /*
         * 2. Line endings to \n.
         *
         * DOCX carries \r\n, some PDF extractions emit bare \r. Three
         * representations of one break would put arbitrary differences into
         * chunk hashes and make "did this document change" unanswerable.
         */
        result = result.replace("\r\n", "\n").replace('\r', '\n');

        /*
         * 3. Parser artefacts with no meaning.
         *
         * The BOM appears at the start of files written by Windows tools. The
         * soft hyphen (U+00AD) is an invisible line-break hint that PDF
         * extraction scatters mid-word, so "agree-ment" is really "agreement" -
         * removing it FIXES the word rather than altering it. Zero-width
         * characters (U+200B-U+200D, U+FEFF) are invisible and only ever
         * corrupt token boundaries.
         *
         * NOTE WHAT IS NOT HERE: no dehyphenation of real line-broken words, no
         * header/footer stripping, no page-number removal. Each would need a
         * heuristic, and a heuristic that fires wrongly deletes legal text.
         */
        result = result.replace("\uFEFF", "")
                .replace("\u00AD", "")
                .replaceAll("[\u200B-\u200D]", "");

        /*
         * 4. Non-breaking and exotic spaces become ordinary spaces.
         *
         * PDF extraction produces U+00A0 and the U+2000-U+200A range freely.
         * They are spaces; treating them as distinct characters means
         * "Rs. 50,000" and "Rs.\u00A050,000" are different strings with
         * different hashes and different embeddings, for no reason a reader
         * would recognise.
         */
        result = result.replaceAll("[\u00A0\u2000-\u200A\u202F\u205F\u3000]", " ");

        /*
         * 5. Collapse runs of spaces and tabs - but NOT newlines.
         *
         * The distinction is the whole point. PDF layout extraction pads
         * columns with long runs of spaces that mean nothing. Newlines mean
         * something, so they are handled separately below.
         */
        result = result.replaceAll("[ \\t]+", " ");

        // 6. Trailing whitespace per line: an artefact, always.
        result = result.replaceAll("[ \\t]+\n", "\n");

        /*
         * 7. Three or more blank lines become exactly one blank line.
         *
         * PARAGRAPH BOUNDARIES ARE PRESERVED, WHICH IS THE POINT. A single
         * blank line separates clauses and the chunker splits on it, so
         * collapsing all blank lines would destroy the structure chunking
         * depends on. What is removed is only the excess: a page break in a PDF
         * can emit a dozen empty lines, and one is as informative as twelve.
         */
        result = result.replaceAll("\n{3,}", "\n\n");

        return result.strip();
    }
}
