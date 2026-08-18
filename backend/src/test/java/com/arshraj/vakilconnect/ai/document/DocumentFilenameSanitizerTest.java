package com.arshraj.vakilconnect.ai.document;

import com.arshraj.vakilconnect.ai.document.service.DocumentFilenameSanitizer;
import com.arshraj.vakilconnect.common.exception.InvalidDocumentNameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filename sanitising, exhaustively, as a unit test.
 *
 * NOT FOLDED INTO THE INTEGRATION TEST. Every case here is a pure
 * string-in/string-out question, and routing each one through Testcontainers,
 * Flyway, a registration, a login and a multipart POST would cost seconds
 * apiece to assert something a function call answers instantly. The IT proves
 * the sanitiser is WIRED IN; this proves it is CORRECT.
 */
@DisplayName("DocumentFilenameSanitizer")
class DocumentFilenameSanitizerTest {

    // ------------------------------------------------------ path traversal

    @Test
    @DisplayName("strips POSIX path components")
    void stripsPosixPath() {
        assertEquals("passwd.txt",
                DocumentFilenameSanitizer.sanitize("../../../etc/passwd.txt"));
        assertEquals("contract.pdf",
                DocumentFilenameSanitizer.sanitize("/var/tmp/contract.pdf"));
    }

    @Test
    @DisplayName("strips Windows path components even though the server is not Windows")
    void stripsWindowsPath() {
        /*
         * THE CASE Paths.get() WOULD MISS. On Linux a backslash is an ordinary
         * filename character, so the JDK's own path handling treats this entire
         * string as one segment and returns it unchanged - storing
         * `C:\Users\me\contract.pdf` as the filename. Handling both separators
         * explicitly is why the sanitiser does not use Paths.get().
         */
        assertEquals("contract.pdf",
                DocumentFilenameSanitizer.sanitize("C:\\Users\\me\\contract.pdf"));
        assertEquals("evil.txt",
                DocumentFilenameSanitizer.sanitize("..\\..\\windows\\system32\\evil.txt"));
    }

    @Test
    @DisplayName("a name made only of traversal is rejected, not silently emptied")
    void pureTraversalIsRejected() {
        // Nothing usable survives, so there is no name to store. Returning ""
        // would violate the NOT NULL column and produce a constraint error
        // instead of a described one.
        assertThrows(InvalidDocumentNameException.class,
                () -> DocumentFilenameSanitizer.sanitize("../../.."));
        assertThrows(InvalidDocumentNameException.class,
                () -> DocumentFilenameSanitizer.sanitize("/"));
    }

    // -------------------------------------------------- hostile characters

    @Test
    @DisplayName("removes CR and LF — the Content-Disposition injection vector")
    void removesControlCharacters() {
        /*
         * A download endpoint arriving in a later phase would put this value
         * into a `Content-Disposition: attachment; filename="..."` header. A
         * newline there ends the header and begins another one the attacker
         * chose. Cleaning at the boundary means that endpoint cannot forget.
         */
        String cleaned = DocumentFilenameSanitizer.sanitize("report\r\nSet-Cookie: x=y.pdf");

        assertFalse(cleaned.contains("\r"));
        assertFalse(cleaned.contains("\n"));
        assertTrue(cleaned.endsWith(".pdf"));
    }

    @Test
    @DisplayName("removes the right-to-left override used to fake an extension")
    void removesBidiOverride() {
        /*
         * U+202E reverses the rendering of everything after it, so
         * "exploit\u202Efdp.exe" DISPLAYS as "exploitexe.pdf" in most UIs. The
         * bytes say .exe, the user's eyes say .pdf. Stripping format characters
         * means the displayed name and the stored name agree.
         */
        String cleaned = DocumentFilenameSanitizer.sanitize("exploit\u202Efdp.exe");

        assertFalse(cleaned.contains("\u202E"));
        assertEquals("exploitfdp.exe", cleaned);
    }

    @Test
    @DisplayName("replaces quotes and reserved characters rather than deleting them")
    void replacesReservedCharacters() {
        // Replaced with underscores so the name stays legible: a user should
        // still recognise the file they uploaded.
        assertEquals("Q1_Q2 report.pdf",
                DocumentFilenameSanitizer.sanitize("Q1:Q2 report.pdf"));
        assertFalse(DocumentFilenameSanitizer.sanitize("say_\"hello\".txt").contains("\""));
    }

    @Test
    @DisplayName("strips leading dots so a file cannot be hidden")
    void stripsLeadingDots() {
        assertEquals("bashrc", DocumentFilenameSanitizer.sanitize(".bashrc"));
        assertThrows(InvalidDocumentNameException.class,
                () -> DocumentFilenameSanitizer.sanitize("..."));
    }

    // --------------------------------------------------------- preservation

    @Test
    @DisplayName("PRESERVES case and accents — this is a display name, not a key")
    void preservesCaseAndAccents() {
        /*
         * The reason TextNormalizer is not reused here. That utility lowercases
         * and strips diacritics because it defines equality for reference data;
         * applying it to a filename would hand the user back
         * "contrat_aout.pdf" for a file they named "Contrat_Août.pdf".
         */
        assertEquals("Contrat_Août.pdf",
                DocumentFilenameSanitizer.sanitize("Contrat_Août.pdf"));
        assertEquals("Rental Agreement FINAL.docx",
                DocumentFilenameSanitizer.sanitize("Rental Agreement FINAL.docx"));
    }

    @Test
    @DisplayName("collapses whitespace and trims")
    void normalisesWhitespace() {
        assertEquals("my contract.pdf",
                DocumentFilenameSanitizer.sanitize("  my   contract.pdf  "));
    }

    @Test
    @DisplayName("null or blank is rejected")
    void nullOrBlankIsRejected() {
        assertThrows(InvalidDocumentNameException.class,
                () -> DocumentFilenameSanitizer.sanitize(null));
        assertThrows(InvalidDocumentNameException.class,
                () -> DocumentFilenameSanitizer.sanitize("   "));
    }

    // ---------------------------------------------------------- truncation

    @Test
    @DisplayName("truncates to the column width while KEEPING the extension")
    void truncatesButKeepsExtension() {
        /*
         * A blunt substring(0, 255) would amputate the extension, and the
         * extension is what the type check keys on - so an over-long name would
         * be rejected as an unsupported type, which is a confusing lie about
         * what went wrong.
         */
        String longName = "a".repeat(400) + ".pdf";

        String cleaned = DocumentFilenameSanitizer.sanitize(longName);

        assertTrue(cleaned.length() <= 255,
                "must fit varchar(255), got " + cleaned.length());
        assertTrue(cleaned.endsWith(".pdf"),
                "the extension must survive truncation, got: " + cleaned);
        assertEquals("pdf", DocumentFilenameSanitizer.extensionOf(cleaned));
    }

    // ----------------------------------------------------------- extension

    @Test
    @DisplayName("extensionOf lowercases with Locale.ROOT, never the platform default")
    void extensionIsLowercasedSafely() {
        /*
         * The Turkish locale maps 'I' to dotless 'ı', so a default-locale
         * toLowerCase would turn "PDF" into "pdf" or "pdf" depending on where
         * the server happens to run. This codebase already hit that class of
         * bug in User.setEmail.
         */
        assertEquals("pdf", DocumentFilenameSanitizer.extensionOf("REPORT.PDF"));
        assertEquals("docx", DocumentFilenameSanitizer.extensionOf("Contract.DocX"));
    }

    @Test
    @DisplayName("extensionOf returns the LAST extension, defeating double extensions")
    void takesTheLastExtension() {
        // "invoice.pdf.exe" is an executable. Reading the first extension - or
        // merely checking that ".pdf" appears anywhere - would accept it.
        assertEquals("exe", DocumentFilenameSanitizer.extensionOf("invoice.pdf.exe"));
    }

    @Test
    @DisplayName("extensionOf returns empty when there is none")
    void noExtension() {
        assertEquals("", DocumentFilenameSanitizer.extensionOf("README"));
        // A leading dot is a name that starts with a dot, not an extension -
        // though the sanitiser strips those before this is ever reached.
        assertEquals("", DocumentFilenameSanitizer.extensionOf(".gitignore"));
        assertEquals("", DocumentFilenameSanitizer.extensionOf("trailing."));
    }
}
