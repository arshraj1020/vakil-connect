package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.document.DocumentFixtures;
import com.arshraj.vakilconnect.ai.document.service.DocumentContentTypeDetector;
import com.arshraj.vakilconnect.common.exception.DocumentExtractionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text extraction, against documents real parsers can actually read.
 *
 * NOTE THE TWO FIXTURE SETS. AI-1's DocumentFixtures produce files that are
 * structurally valid enough to be IDENTIFIED by a magic-byte detector - which
 * is all that phase needed. A parser needs far more: a page tree and a content
 * stream, or an OPC relationship graph. So AI-1's fixtures serve here as
 * genuine MALFORMED documents, and IngestionFixtures supplies parseable ones.
 */
@DisplayName("TikaDocumentTextExtractor")
class TikaDocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new TikaDocumentTextExtractor();

    @Test
    @DisplayName("TXT extracts without going through Tika at all")
    void extractsTxt() {
        // AI-1 already proved these bytes are strict UTF-8 with no control
        // characters, so the text IS the decoded bytes. A parser round trip
        // would re-derive a known fact and add a charset guess that could only
        // make it worse.
        String text = extractor.extract(
                IngestionFixtures.legalText().getBytes(StandardCharsets.UTF_8),
                DocumentContentTypeDetector.TXT);

        assertTrue(text.contains("RESIDENTIAL TENANCY AGREEMENT"));
        assertTrue(text.contains("Rs. 45,000"));
    }

    @Test
    @DisplayName("PDF extracts the text in its content stream")
    void extractsPdf() {
        String text = extractor.extract(
                IngestionFixtures.pdfWithText(), DocumentContentTypeDetector.PDF);

        assertTrue(text.contains(IngestionFixtures.PDF_MARKER),
                "expected the content-stream text, got " + text.length() + " characters");
    }

    @Test
    @DisplayName("DOCX extracts the document body")
    void extractsDocx() {
        String text = extractor.extract(
                IngestionFixtures.docxWithText(), DocumentContentTypeDetector.DOCX);

        assertTrue(text.contains(IngestionFixtures.DOCX_MARKER));
        assertTrue(text.contains("refunded within 30 days"));
    }

    // ---------------------------------------------------------- rejections

    @Test
    @DisplayName("a malformed PDF becomes a controlled exception, not a parser stack trace")
    void rejectsMalformedPdf() {
        // AI-1's fixture: valid magic number, no page tree, no content stream.
        byte[] malformed = DocumentFixtures.pdf();

        DocumentExtractionException thrown = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(malformed, DocumentContentTypeDetector.PDF));

        assertMessageIsSafe(thrown);
    }

    @Test
    @DisplayName("a malformed DOCX becomes a controlled exception")
    void rejectsMalformedDocx() {
        // AI-1's fixture has word/document.xml but no _rels/.rels, so POI's
        // OPCPackage cannot find the main document part.
        byte[] malformed = DocumentFixtures.docx();

        DocumentExtractionException thrown = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(malformed, DocumentContentTypeDetector.DOCX));

        assertMessageIsSafe(thrown);
    }

    @Test
    @DisplayName("truncated binary input is rejected cleanly")
    void rejectsTruncatedInput() {
        byte[] docx = IngestionFixtures.docxWithText();
        byte[] truncated = new byte[docx.length / 2];
        System.arraycopy(docx, 0, truncated, 0, truncated.length);

        assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(truncated, DocumentContentTypeDetector.DOCX));
    }

    @Test
    @DisplayName("empty bytes are rejected")
    void rejectsEmptyBytes() {
        assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(new byte[0], DocumentContentTypeDetector.PDF));
        assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(null, DocumentContentTypeDetector.TXT));
    }

    @Test
    @DisplayName("a document yielding NO TEXT is a failure, not an empty success")
    void rejectsEmptyExtraction() {
        /*
         * The scanned-PDF case: pages of images with no text layer. Letting it
         * through would create a READY document with zero chunks - healthy in
         * the API, silently contributing nothing to any answer.
         */
        DocumentExtractionException thrown = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract("   \n\t  ".getBytes(StandardCharsets.UTF_8),
                        DocumentContentTypeDetector.TXT));

        assertTrue(thrown.getMessage().toLowerCase().contains("no text")
                        || thrown.getMessage().toLowerCase().contains("scanned"),
                "the message should point at OCR/scanning: " + thrown.getMessage());
    }

    /**
     * THE SECURITY ASSERTION, applied to every rejection path.
     *
     * Tika, PDFBox and POI embed byte offsets, object numbers and sometimes
     * fragments of document content in their exception messages. None of that
     * may reach a user or a log, so the extractor's message is fixed and never
     * interpolates the cause's.
     */
    private static void assertMessageIsSafe(DocumentExtractionException thrown) {
        String message = thrown.getMessage();

        assertEquals("This document could not be read. It may be corrupt or password-protected.",
                message, "the message must be the fixed, safe one");

        for (String leak : new String[]{ "offset", "COSObject", "OPCPackage",
                "org.apache", "Exception", "at line" }) {
            assertFalse(message.contains(leak), "parser internal leaked: " + leak);
        }
        assertFalse(message.contains(IngestionFixtures.PDF_MARKER));
    }
}
