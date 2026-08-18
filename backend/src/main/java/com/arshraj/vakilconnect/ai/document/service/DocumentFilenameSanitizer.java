package com.arshraj.vakilconnect.ai.document.service;

import com.arshraj.vakilconnect.common.exception.InvalidDocumentNameException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns a client-supplied filename into something safe to store and display.
 *
 * WHY THIS MATTERS EVEN THOUGH NOTHING TOUCHES THE FILESYSTEM. Document bytes
 * go into PostgreSQL, so `../../etc/passwd` cannot traverse anything today -
 * the classic exploit is structurally unavailable. The sanitiser is not
 * pointless for that reason; it is what stops the value becoming dangerous
 * LATER, and there are three concrete routes:
 *
 *   * a download endpoint (AI-2 or beyond) setting `Content-Disposition:
 *     attachment; filename="..."`, where an embedded quote or CRLF is header
 *     injection;
 *   * anything that writes a temporary file during text extraction, where the
 *     path suddenly IS a path;
 *   * the frontend rendering the name, where control characters and
 *     right-to-left override marks are a spoofing tool - U+202E turns
 *     "exploitfdp.exe" into something that displays as "exploitexe.pdf".
 *
 * Sanitising once at the boundary means none of those has to remember. A value
 * that is only cleaned at the point of use is a value that will eventually be
 * used somewhere that forgot.
 *
 * DOES NOT USE {@code Paths.get(...).getFileName()}. That is the obvious
 * approach and it is platform-dependent: on Linux a backslash is an ordinary
 * filename character, so a Windows client's `C:\Users\me\contract.pdf` survives
 * intact and gets stored as one long name. Both separators are handled
 * explicitly below.
 *
 * DOES NOT USE {@code TextNormalizer}. That utility lowercases and strips
 * accents because it defines equality for reference data. A filename is
 * displayed back to the person who chose it: "Contrat_Août.pdf" must not become
 * "contrat_aout.pdf".
 */
public final class DocumentFilenameSanitizer {

    /** Matches varchar(255) in V8, so a too-long value is impossible rather than merely unlikely. */
    static final int MAX_LENGTH = 255;

    private DocumentFilenameSanitizer() {
    }

    /**
     * @return a safe filename, never null, never blank, at most
     *         {@link #MAX_LENGTH} characters
     * @throws InvalidDocumentNameException if nothing usable survives
     */
    public static String sanitize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new InvalidDocumentNameException();
        }

        /*
         * NFC FIRST, BEFORE ANY OTHER CHECK.
         *
         * Order is load-bearing. Unicode has several ways to spell the same
         * name, and some decompose into sequences containing characters the
         * later rules care about. Normalising afterwards could reintroduce
         * something that was just removed; normalising first means every
         * subsequent rule sees one canonical form.
         *
         * NFC (composed), not NFD: it is the form the web has standardised on,
         * and it preserves the accented characters a user typed rather than
         * splitting them into base + combining mark.
         */
        String name = Normalizer.normalize(rawName, Normalizer.Form.NFC);

        /*
         * Strip the path. Everything up to and including the last separator
         * goes, handling BOTH separators regardless of the server's platform.
         * This is what neutralises `../../etc/passwd` and
         * `C:\Users\me\contract.pdf` alike - not by rejecting them, but by
         * keeping only the last segment, which is the part the user meant.
         */
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        /*
         * Remove control characters, the NUL byte, and Unicode format
         * characters.
         *
         * \p{Cc} is C0/C1 controls - CR and LF among them, which is the header
         * injection vector. \p{Cf} is the format category, which is where the
         * bidirectional overrides live (U+202E and friends) - the extension
         * spoofing trick. \p{Co} is private use, which no legitimate filename
         * needs and which renders unpredictably.
         *
         * Removed rather than rejected: a stray character should not fail an
         * upload the user believes is fine.
         */
        name = name.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}]", "");

        /*
         * Quotes, angle brackets, pipes, colons and asterisks.
         *
         * A double quote terminates a Content-Disposition filename parameter,
         * and the rest are either reserved on Windows or shell metacharacters.
         * Replaced with an underscore rather than deleted so the name stays
         * legible: `Q1:Q2 report.pdf` reads better as `Q1_Q2 report.pdf` than
         * as `Q1Q2 report.pdf`.
         */
        name = name.replaceAll("[\"'<>|:*?]", "_");

        // Collapse runs of whitespace and trim. A name that is only spaces is
        // caught by the blank check below.
        name = name.replaceAll("\\s+", " ").trim();

        /*
         * Leading dots.
         *
         * ".", ".." and "..." are not names, and a leading dot makes a file
         * hidden on Unix - which, if this is ever written to disk, hides it
         * from exactly the tooling an operator would use to find it. Trimming
         * leading dots also finishes off any traversal fragment that survived
         * the path strip.
         *
         * Trailing dots are removed too: Windows silently strips them, so
         * "report.pdf." and "report.pdf" are the same file there and storing
         * both invites confusion.
         */
        name = name.replaceAll("^\\.+", "").replaceAll("\\.+$", "");

        if (name.isBlank()) {
            throw new InvalidDocumentNameException();
        }

        /*
         * Truncate from the FRONT, keeping the extension.
         *
         * A blunt substring(0, 255) would cut the extension off a long name,
         * and the extension is what the type check keys on - so the upload
         * would fail with "unsupported type" for a file that was merely
         * verbosely named. Preserving the suffix keeps the failure honest.
         */
        if (name.length() > MAX_LENGTH) {
            String extension = extensionOf(name);
            int keep = MAX_LENGTH - (extension.isEmpty() ? 0 : extension.length() + 1);
            name = name.substring(0, Math.max(1, keep))
                    + (extension.isEmpty() ? "" : "." + extension);
        }

        return name;
    }

    /**
     * The lowercase extension, without the dot, or an empty string.
     *
     * LOWERCASED WITH Locale.ROOT, never the platform default. The Turkish
     * locale maps 'I' to dotless 'ı', so `REPORT.PDF` on a Turkish-locale host
     * would yield "pdf" only by luck - and a file named `.TXT` would become
     * ".txt" or ".tıt" depending on where the server happened to be running.
     * This codebase already hit that class of bug in User.setEmail.
     *
     * A leading dot does not count as a separator: ".gitignore" has no
     * extension, it is a name that starts with a dot.
     */
    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
