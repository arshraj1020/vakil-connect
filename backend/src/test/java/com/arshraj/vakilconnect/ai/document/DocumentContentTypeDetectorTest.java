package com.arshraj.vakilconnect.ai.document;

import com.arshraj.vakilconnect.ai.document.service.DocumentContentTypeDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Type detection from bytes.
 *
 * THIS IS THE SECURITY CONTROL THAT STOPS "RENAME IT TO .PDF". The extension
 * and the client's Content-Type header are both chosen by the caller; only the
 * bytes are not. Each test below is a file somebody would actually try.
 */
@DisplayName("DocumentContentTypeDetector")
class DocumentContentTypeDetectorTest {

    @Test
    @DisplayName("identifies a PDF by its magic number")
    void detectsPdf() {
        assertEquals(Optional.of(DocumentContentTypeDetector.PDF),
                DocumentContentTypeDetector.detect(DocumentFixtures.pdf()));
    }

    /* ---------------------------------------------------------------------
     * DOCX / ZIP structure.
     *
     * The five cases below are the whole contract for archive detection, and
     * they exist as a group because the obvious implementation passes four of
     * them. Walking a ZIP with ZipInputStream and returning true on finding the
     * entry name `word/document.xml` accepts a TRUNCATED archive: that reader
     * parses the LOCAL headers at the front of the file and never reads the
     * central directory at the end, so cutting a DOCX in half leaves every
     * entry name intact while destroying the archive.
     * ------------------------------------------------------------------- */

    @Test
    @DisplayName("1. a valid DOCX is accepted")
    void detectsDocx() {
        assertEquals(Optional.of(DocumentContentTypeDetector.DOCX),
                DocumentContentTypeDetector.detect(DocumentFixtures.docx()));
    }

    @Test
    @DisplayName("2. a bare ZIP without word/document.xml is rejected")
    void zipIsNotAutomaticallyDocx() {
        /*
         * JARs, XLSX, PPTX, ODT, EPUB and APK files all begin with the same
         * four bytes as a DOCX. Stopping at the magic number would accept an
         * Android package as a Word document, and AI-2 would then try to
         * extract text from it.
         */
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.detect(DocumentFixtures.zipThatIsNotDocx()));
    }

    @Test
    @DisplayName("3. a truncated ZIP is rejected even though its entry names survive")
    void rejectsTruncatedZip() {
        /*
         * THE REGRESSION TEST FOR THE ACTUAL BUG.
         *
         * Half of a valid DOCX still contains the local file header naming
         * `word/document.xml`, because those headers precede the data. What it
         * has lost is the central directory and the End of Central Directory
         * record - the archive's own index of itself - which is precisely what
         * "corrupt" means and precisely what a streaming reader never looks at.
         *
         * The assertion below is what the naive implementation failed.
         */
        byte[] docx = DocumentFixtures.docx();
        byte[] truncated = new byte[docx.length / 2];
        System.arraycopy(docx, 0, truncated, 0, truncated.length);

        // The name really is still in there - so the rejection is not coming
        // from the marker being absent.
        assertTrue(new String(truncated, StandardCharsets.ISO_8859_1).contains("word/document.xml"),
                "fixture precondition: the truncated archive must still name the entry, "
                        + "otherwise this test proves nothing about structural validation");

        assertEquals(Optional.empty(), DocumentContentTypeDetector.detect(truncated));
    }

    @Test
    @DisplayName("4. a complete archive with CORRUPTED word/document.xml data is rejected")
    void rejectsCorruptedEntryData() {
        /*
         * The case the structural check alone cannot catch, and the reason the
         * detector also decompresses the entry.
         *
         * This archive has a valid EOCD and a valid central directory that
         * genuinely declares `word/document.xml` - so both index-level checks
         * pass. The entry's bytes are damaged, which surfaces only as a broken
         * deflate stream or a CRC mismatch when it is actually read.
         */
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.detect(
                        DocumentFixtures.docxWithCorruptedEntryData()));
    }

    @Test
    @DisplayName("5. ZIP magic followed by noise is rejected")
    void rejectsInvalidZip() {
        // No central directory, no EOCD, nothing a ZIP reader can use. The
        // magic number is the only thing about it that is ZIP-shaped.
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.detect(DocumentFixtures.invalidZip()));
    }

    @Test
    @DisplayName("an archive truncated by a single trailing byte is rejected")
    void rejectsMinimallyTruncatedArchive() {
        /*
         * The boundary case. Removing one byte leaves the entire archive intact
         * except that the EOCD's declared comment length no longer matches the
         * bytes remaining after it - which is exactly the consistency check
         * that stops a stray signature inside compressed data being mistaken
         * for the real record.
         */
        byte[] docx = DocumentFixtures.docx();
        byte[] clipped = new byte[docx.length - 1];
        System.arraycopy(docx, 0, clipped, 0, clipped.length);

        assertEquals(Optional.empty(), DocumentContentTypeDetector.detect(clipped));
    }

    @Test
    @DisplayName("identifies plain text")
    void detectsText() {
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt()));
    }

    @Test
    @DisplayName("accepts non-ASCII UTF-8 text")
    void detectsUtf8Text() {
        // A legal document in Hindi or with typographic quotes is still text.
        // Rejecting multi-byte UTF-8 would fail real users on an Indian legal
        // platform.
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(
                        DocumentFixtures.txt("किरायेदारी समझौता — “final”")));
    }

    // -------------------------------------------------------------- refusals

    @Test
    @DisplayName("REJECTS binary content containing NUL bytes")
    void rejectsBinary() {
        /*
         * The core of the plain-text rule. Essentially every binary format -
         * ELF, PE, Mach-O, class files, most images - carries NUL bytes early,
         * and no legitimate text file carries one at all. Without this,
         * "rename your malware to .txt" is a working upload.
         */
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.detect(DocumentFixtures.binaryWithNulBytes()));
    }

    @Test
    @DisplayName("REJECTS invalid UTF-8 even when it contains no NUL byte")
    void rejectsInvalidUtf8() {
        /*
         * The other half, and the reason the decoder is configured to REPORT.
         * `new String(bytes, UTF_8)` silently substitutes U+FFFD for malformed
         * input and therefore NEVER fails, which would make this check
         * decorative. These bytes are a lone continuation byte and an
         * incomplete sequence - not valid UTF-8, and not a NUL in sight.
         */
        byte[] invalid = { (byte) 0x80, (byte) 0xC3, (byte) 0x28, (byte) 0xFF };

        assertEquals(Optional.empty(), DocumentContentTypeDetector.detect(invalid));
    }

    @Test
    @DisplayName("REJECTS a file of only whitespace")
    void rejectsWhitespaceOnly() {
        // Bytes, but no text. Storing it would queue a document with nothing to
        // extract, which AI-2 would fail on every attempt.
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt("   \n\t  \n ")));
    }

    @Test
    @DisplayName("REJECTS empty and null input")
    void rejectsEmpty() {
        assertEquals(Optional.empty(), DocumentContentTypeDetector.detect(new byte[0]));
        assertEquals(Optional.empty(), DocumentContentTypeDetector.detect(null));
    }

    @Test
    @DisplayName("archive input truncated at ANY length never throws and is never a DOCX")
    void malformedArchiveDoesNotThrow() {
        /*
         * WHAT THIS TEST ASSERTS, AND WHAT IT DELIBERATELY DOES NOT.
         *
         * It asserts the two properties that hold at every truncation length:
         * detect() returns a VALUE rather than throwing, and the result is
         * never DOCX. An exception escaping here would reach the controller as
         * a 500 instead of the described 415 the caller needs.
         *
         * It does NOT assert Optional.empty() at every length, and the earlier
         * version that did was simply wrong. Truncating a DOCX to ONE byte does
         * not leave a malformed archive - it leaves the single byte 0x50, which
         * is the letter "P". That is a valid one-character text file, and
         * text/plain is the correct answer for it. Demanding empty there would
         * have forced a minimum-length rule, which would reject legitimate tiny
         * text files while still accepting long binaries that avoid NUL.
         *
         * The stricter verdict - empty, not text - is asserted in
         * truncatedArchiveIsNotDowngradedToText() below, for the lengths where
         * the input is still recognisably archive-shaped.
         */
        byte[] docx = DocumentFixtures.docx();

        for (int keep : new int[]{ 1, 2, 3, 4, 8, 16, 30,
                docx.length / 3, docx.length / 2, docx.length - 2, docx.length - 1 }) {

            byte[] clipped = Arrays.copyOf(docx, keep);

            Optional<String> verdict = DocumentContentTypeDetector.detect(clipped);

            assertNotEquals(Optional.of(DocumentContentTypeDetector.DOCX), verdict,
                    "truncated to " + keep + " bytes must never pass as a DOCX");
        }
    }

    @Test
    @DisplayName("a truncation that is still ZIP-shaped is rejected, NOT downgraded to text")
    void truncatedArchiveIsNotDowngradedToText() {
        /*
         * THE GAP THIS TEST WAS WRITTEN FOR.
         *
         * `PK\003\004` is valid UTF-8 - two letters followed by the control
         * characters ETX and EOT - and contains no NUL byte. A plain-text rule
         * checking only "decodes as UTF-8, no NUL" therefore classified the
         * header fragment of a truncated Word document as text/plain: a silent
         * downgrade, so a corrupt DOCX renamed to .txt would have been stored.
         *
         * Control characters are what distinguish a binary header from text.
         */
        byte[] docx = DocumentFixtures.docx();

        for (int keep : new int[]{ 4, 8, 16, 30 }) {
            assertEquals(Optional.empty(),
                    DocumentContentTypeDetector.detect(Arrays.copyOf(docx, keep)),
                    "the first " + keep + " bytes of a DOCX are a binary header, not text");
        }
    }

    // ------------------------------------------------ the text-character rule

    @Test
    @DisplayName("a one-character text file IS text — the rule is about characters, not length")
    void acceptsTinyTextFile() {
        /*
         * The counterweight to the test above, and the reason no minimum-length
         * rule exists. "P" is a legitimate text file. So is a single digit, or
         * a single emoji. Any length threshold chosen to reject `PK\003\004`
         * would reject these too, and would still accept a megabyte of binary
         * that happened to avoid NUL - so length is simply the wrong axis.
         */
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt("P")));
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt("7")));
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt("अ")));
    }

    @Test
    @DisplayName("layout whitespace is accepted: tab, newline, carriage return, form feed")
    void acceptsLayoutWhitespace() {
        // A real document is full of these. Rejecting them would refuse every
        // multi-line file, which is every file anyone will actually upload.
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt(
                        "Clause 1.\tPayment\r\nClause 2.\tNotice\n\fClause 3.\tTermination")));
    }

    @Test
    @DisplayName("C0 control characters other than layout whitespace are rejected")
    void rejectsControlCharacters() {
        /*
         * Written as NUMERIC CODE POINTS, not as literal control bytes in the
         * source. A raw ETX embedded in a Java file is invisible in a diff,
         * silently stripped by some editors, and indistinguishable from a typo -
         * a poor way to express the exact values a security rule turns on.
         *
         * SOH, STX, ETX, EOT, BEL, BS, VT, SUB, ESC. All valid UTF-8, none of
         * them NUL, and none of them something a human typed into a contract.
         * ETX and EOT matter most: they are bytes 3 and 4 of every ZIP header.
         */
        for (int control : new int[]{ 0x01, 0x02, 0x03, 0x04, 0x07, 0x08, 0x0B, 0x1A, 0x1B }) {
            String withControl = "Agreement" + (char) control + " text";

            assertEquals(Optional.empty(),
                    DocumentContentTypeDetector.detect(DocumentFixtures.txt(withControl)),
                    String.format("U+%04X must not be accepted as text", control));
        }
    }

    @Test
    @DisplayName("DEL and the C1 control range are rejected")
    void rejectsDelAndC1Controls() {
        /*
         * C1 (U+0080-U+009F) deserves its own case: those code points only
         * arise from text that was really Latin-1 or Windows-1252 and got
         * decoded as UTF-8, or from binary. Never from something typed. Both
         * boundary values are asserted so an off-by-one in the range check is
         * caught.
         */
        for (int control : new int[]{ 0x7F, 0x80, 0x90, 0x9F }) {
            String withControl = "text" + (char) control + "more";

            assertEquals(Optional.empty(),
                    DocumentContentTypeDetector.detect(DocumentFixtures.txt(withControl)),
                    String.format("U+%04X must not be accepted as text", control));
        }
    }

    @Test
    @DisplayName("characters just above the C1 range are accepted, including astral ones")
    void acceptsNonControlUnicode() {
        /*
         * The other side of the boundary. U+00A0 is the first code point above
         * C1 and is a no-break space - genuine text punctuation. Asserting it
         * proves the range check ends where it should rather than swallowing
         * everything non-ASCII along with the controls.
         */
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(
                        DocumentFixtures.txt("Rs." + (char) 0xA0 + "50,000 payable")));

        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(DocumentFixtures.txt("caf\u00E9 terms")));

        // A surrogate pair, so the char-by-char scan is proved not to trip on
        // one half of an astral character.
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.detect(
                        DocumentFixtures.txt("signed " + new String(Character.toChars(0x1F4C4))
                                + " copy")));
    }

    // ------------------------------------------------------ extension mapping

    @Test
    @DisplayName("maps the three allowed extensions and nothing else")
    void extensionMapping() {
        assertEquals(Optional.of(DocumentContentTypeDetector.PDF),
                DocumentContentTypeDetector.expectedTypeForExtension("pdf"));
        assertEquals(Optional.of(DocumentContentTypeDetector.DOCX),
                DocumentContentTypeDetector.expectedTypeForExtension("docx"));
        assertEquals(Optional.of(DocumentContentTypeDetector.TXT),
                DocumentContentTypeDetector.expectedTypeForExtension("txt"));

        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.expectedTypeForExtension("exe"));
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.expectedTypeForExtension("doc"));
        assertEquals(Optional.empty(),
                DocumentContentTypeDetector.expectedTypeForExtension(""));
    }

    @Test
    @DisplayName("the allowed set is exactly pdf, docx, txt")
    void allowedExtensionsArePinned() {
        // Widening what can be uploaded should be a deliberate decision that
        // breaks a test, not a one-word edit nobody reviews.
        assertEquals(3, DocumentContentTypeDetector.ALLOWED_EXTENSIONS.size());
        assertTrue(DocumentContentTypeDetector.ALLOWED_EXTENSIONS
                .containsAll(java.util.Set.of("pdf", "docx", "txt")));
    }

    @Test
    @DisplayName("a spoofed file is caught by comparing detected against expected")
    void spoofedFileIsCaught() {
        /*
         * The comparison the upload path performs, asserted directly. The user
         * claims .pdf; the bytes are plain text. Neither value alone is
         * suspicious - the disagreement is.
         */
        byte[] notReallyAPdf = "I am definitely a PDF, trust me.".getBytes(StandardCharsets.UTF_8);

        Optional<String> detected = DocumentContentTypeDetector.detect(notReallyAPdf);
        Optional<String> expected = DocumentContentTypeDetector.expectedTypeForExtension("pdf");

        assertEquals(Optional.of(DocumentContentTypeDetector.TXT), detected);
        assertEquals(Optional.of(DocumentContentTypeDetector.PDF), expected);
        assertTrue(!detected.equals(expected), "the mismatch is what the upload path rejects");
    }
}
