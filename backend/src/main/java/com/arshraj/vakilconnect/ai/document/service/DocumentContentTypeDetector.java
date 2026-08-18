package com.arshraj.vakilconnect.ai.document.service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Works out what a file ACTUALLY is, from its bytes.
 *
 * THE CLIENT'S Content-Type IS NEVER CONSULTED. Not cross-checked, not used as
 * a hint, not stored - the multipart part's header is attacker-controlled, so
 * treating it as evidence would mean anyone can call anything a PDF. Nor is the
 * extension trusted on its own, for the same reason. Both are inputs the caller
 * chooses; only the bytes are not.
 *
 * The upload path uses both together: the extension says what the user CLAIMS,
 * this class says what it IS, and a mismatch is a rejection. That catches the
 * two realistic cases - an unsupported file renamed to .pdf, and a genuine PDF
 * saved as .txt - without needing to guess which one the user meant.
 *
 * NO APACHE TIKA. Tika is the right tool for identifying arbitrary formats and
 * it arrives in AI-2 for text extraction. Three formats, checked by three short
 * rules, do not justify pulling its dependency tree in a phase whose whole job
 * is the document record - and every byte of this class is auditable, which
 * matters more for a security control than breadth does.
 */
public final class DocumentContentTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String TXT = "text/plain";

    /** Extensions the upload endpoint accepts. Lowercase, no dot. */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    /** `%PDF-`. Every PDF begins with it; the spec requires it in the first bytes. */
    private static final byte[] PDF_MAGIC = { 0x25, 0x50, 0x44, 0x46, 0x2D };

    /** `PK\003\004` - the local file header that starts every non-empty ZIP archive. */
    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

    /** The entry an OOXML word processing document must contain. */
    private static final String DOCX_MARKER_ENTRY = "word/document.xml";

    /**
     * Upper bound on ZIP entries examined before giving up.
     *
     * A ZIP with millions of tiny entries is a cheap denial-of-service against
     * anything that walks it, and a document with more than a few hundred parts
     * is not a Word file anyone is legitimately uploading.
     */
    private static final int MAX_ZIP_ENTRIES_SCANNED = 512;

    /**
     * Ceiling on bytes inflated while proving the DOCX entry is readable.
     *
     * The upload size limit bounds the COMPRESSED input, not the decompressed
     * output - a 10MB archive of zeroes expands to gigabytes. Since verifying
     * the entry means actually decompressing it, that cap has to be restated
     * here in decompressed terms.
     */
    private static final long MAX_INFLATED_BYTES = 32L * 1024 * 1024;

    /* ---------------------------------------------------------------------
     * ZIP structure constants.
     *
     * A ZIP's authoritative index is its CENTRAL DIRECTORY, which lives at the
     * END of the file and is described by an End of Central Directory record.
     * The per-entry LOCAL headers at the front are a convenience for streaming
     * readers and are not authoritative - which is the whole reason the
     * constants below exist. See the note on looksLikeDocx().
     * ------------------------------------------------------------------- */

    /** `PK\005\006`, little-endian. Marks the End of Central Directory record. */
    private static final int EOCD_SIGNATURE = 0x06054b50;

    /** `PK\001\002`, little-endian. Starts each central directory file header. */
    private static final int CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50;

    /** An EOCD record with an empty comment. */
    private static final int EOCD_MIN_LENGTH = 22;

    /** Fixed portion of a central directory file header, before the name. */
    private static final int CENTRAL_FILE_HEADER_FIXED_LENGTH = 46;

    /** The archive comment length field is 16-bit, so the EOCD is within this of the end. */
    private static final int MAX_ZIP_COMMENT_LENGTH = 0xFFFF;

    private DocumentContentTypeDetector() {
    }

    /**
     * @return the detected media type, or empty if the bytes are none of the
     *         three supported formats
     */
    public static Optional<String> detect(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        if (startsWith(content, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, ZIP_MAGIC) && looksLikeDocx(content)) {
            return Optional.of(DOCX);
        }
        if (looksLikePlainText(content)) {
            return Optional.of(TXT);
        }
        return Optional.empty();
    }

    /** The media type an extension is expected to carry, or empty if unsupported. */
    public static Optional<String> expectedTypeForExtension(String extension) {
        return switch (extension) {
            case "pdf" -> Optional.of(PDF);
            case "docx" -> Optional.of(DOCX);
            case "txt" -> Optional.of(TXT);
            default -> Optional.empty();
        };
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * A ZIP is not a DOCX, and an entry NAME is not proof of anything.
     *
     * THREE CHECKS, IN INCREASING COST, AND ALL THREE ARE NECESSARY.
     *
     * 1. The archive must be STRUCTURALLY COMPLETE - it must have a valid End
     *    of Central Directory record and a central directory that stays inside
     *    the file.
     *
     * 2. The central directory must DECLARE `word/document.xml`. That is what
     *    separates a DOCX from a JAR, XLSX, PPTX, ODT, EPUB or APK, all of
     *    which share ZIP's magic number.
     *
     * 3. That entry must actually DECOMPRESS, with its CRC intact.
     *
     * WHY THE FIRST CHECK EXISTS AT ALL - this is the bug this method was
     * rewritten to fix. The obvious implementation walks the archive with
     * ZipInputStream and returns true on finding the marker entry name. It is
     * wrong, and it fails open. ZipInputStream is a STREAMING reader: it parses
     * the LOCAL file headers stored in front of each entry's data, from the
     * beginning of the file forward. It never reads the central directory,
     * because the central directory is at the END and a stream may not be able
     * to seek there.
     *
     * The consequence is that TRUNCATING AN ARCHIVE DOES NOT REMOVE ITS ENTRY
     * NAMES. Cut a valid DOCX in half and the local headers at the front - and
     * therefore the name `word/document.xml` - survive intact, while the
     * central directory, the EOCD and most of the actual document are gone.
     * The naive check happily reports DOCX for a file that no ZIP tool on earth
     * can open. Entry names are attacker-supplied metadata; the central
     * directory is the archive's own index of itself, and its absence is
     * exactly what "corrupt" means.
     *
     * NO NEW DEPENDENCY, and none is needed. {@code ZipFile} would validate the
     * central directory for us but requires a {@code File} - writing an
     * unvalidated upload to disk to find out whether it is safe is a worse
     * trade than parsing 22 bytes of header. The EOCD format is fixed, tiny and
     * documented in APPNOTE.TXT.
     *
     * Anything malformed is treated as "not a DOCX" rather than propagated:
     * this method answers a yes/no question about hostile input, and the
     * caller's rejection path already produces a described error.
     */
    private static boolean looksLikeDocx(byte[] content) {
        int endOfCentralDirectory = findEndOfCentralDirectory(content);
        if (endOfCentralDirectory < 0) {
            // No index. Truncated, or never a complete archive.
            return false;
        }
        if (!centralDirectoryDeclares(content, endOfCentralDirectory, DOCX_MARKER_ENTRY)) {
            return false;
        }
        return markerEntryIsReadable(content);
    }

    /**
     * Locates the End of Central Directory record, or -1.
     *
     * Searched BACKWARDS from the end because the record is last, and its
     * position depends on a trailing comment of up to 64KB. The scan is bounded
     * by that maximum, so it is O(64KB) regardless of file size.
     *
     * THE COMMENT-LENGTH CHECK IS NOT DECORATION. Compressed data is
     * high-entropy, so the four EOCD signature bytes can and do appear inside
     * it by chance. Requiring the record's declared comment length to equal the
     * bytes actually remaining after it rejects those coincidences: a real EOCD
     * describes the end of the file exactly.
     */
    private static int findEndOfCentralDirectory(byte[] content) {
        if (content.length < EOCD_MIN_LENGTH) {
            return -1;
        }
        int earliest = Math.max(0, content.length - EOCD_MIN_LENGTH - MAX_ZIP_COMMENT_LENGTH);

        for (int i = content.length - EOCD_MIN_LENGTH; i >= earliest; i--) {
            if (readIntLe(content, i) != EOCD_SIGNATURE) {
                continue;
            }
            int commentLength = readShortLe(content, i + 20);
            if (i + EOCD_MIN_LENGTH + commentLength == content.length) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Walks the central directory and reports whether it names {@code target}.
     *
     * EVERY OFFSET IS BOUNDS-CHECKED against the array, and the walk must
     * complete cleanly - a directory that runs off the end of the file, names
     * an entry beyond it, or has a bad header signature means the archive is
     * inconsistent, and an inconsistent archive is rejected even if the target
     * name appeared earlier in it. This is parsing hostile input, so "fail on
     * anything unexpected" is the only defensible posture.
     *
     * ZIP64 IS DELIBERATELY REFUSED. When an archive exceeds 65535 entries or
     * 4GB, the classic fields hold sentinel values (0xFFFF / 0xFFFFFFFF) and
     * the real numbers live in a separate ZIP64 record. Parsing that is real
     * work for a case that cannot occur here: the upload limit is 10MB. A
     * sentinel therefore means either a corrupt file or something well outside
     * what this endpoint accepts, and both should be rejected.
     */
    private static boolean centralDirectoryDeclares(byte[] content, int eocd, String target) {
        int entryCount = readShortLe(content, eocd + 10);
        int directorySize = readIntLe(content, eocd + 12);
        int directoryOffset = readIntLe(content, eocd + 16);

        // 0xFFFF entries, or a size/offset of 0xFFFFFFFF (which reads as -1).
        if (entryCount == 0xFFFF || directorySize == -1 || directoryOffset == -1) {
            return false;
        }
        if (entryCount <= 0 || entryCount > MAX_ZIP_ENTRIES_SCANNED) {
            return false;
        }
        if (directoryOffset < 0 || directorySize < 0
                || (long) directoryOffset + directorySize > content.length) {
            return false;
        }

        boolean found = false;
        int position = directoryOffset;

        for (int i = 0; i < entryCount; i++) {
            if ((long) position + CENTRAL_FILE_HEADER_FIXED_LENGTH > content.length) {
                return false;
            }
            if (readIntLe(content, position) != CENTRAL_FILE_HEADER_SIGNATURE) {
                return false;
            }

            int nameLength = readShortLe(content, position + 28);
            int extraLength = readShortLe(content, position + 30);
            int commentLength = readShortLe(content, position + 32);
            int nameStart = position + CENTRAL_FILE_HEADER_FIXED_LENGTH;

            if ((long) nameStart + nameLength > content.length) {
                return false;
            }
            if (target.equals(new String(content, nameStart, nameLength, StandardCharsets.UTF_8))) {
                // Recorded, not returned: the rest of the directory still has
                // to parse cleanly for the archive to count as intact.
                found = true;
            }

            position = nameStart + nameLength + extraLength + commentLength;
        }

        return found;
    }

    /**
     * Decompresses the marker entry to prove it is genuinely readable.
     *
     * THE ONLY CHECK THAT TOUCHES THE ACTUAL DATA, and the one that catches an
     * archive whose index is intact but whose contents are damaged - a valid
     * EOCD and central directory can sit after corrupted or overwritten entry
     * bytes, and the first two checks would pass.
     *
     * Reading the entry to end-of-stream is what makes ZipInputStream verify
     * the entry's stored CRC, so a single flipped byte is caught rather than
     * silently producing garbage for AI-2 to extract text from.
     *
     * BOUNDED, because this is the one place a zip bomb could bite: the upload
     * limit caps compressed size, and MAX_INFLATED_BYTES caps what that is
     * allowed to expand into. An entry that exceeds it is refused rather than
     * allowed to exhaust the heap.
     */
    private static boolean markerEntryIsReadable(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            byte[] buffer = new byte[8192];
            int scanned = 0;
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null && scanned++ < MAX_ZIP_ENTRIES_SCANNED) {
                if (!DOCX_MARKER_ENTRY.equals(entry.getName())) {
                    continue;
                }

                long inflated = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    inflated += read;
                    if (inflated > MAX_INFLATED_BYTES) {
                        return false;
                    }
                }

                // An entry that decompresses to nothing is not a document.
                return inflated > 0;
            }
            return false;

        } catch (Exception e) {
            /*
             * A CRC mismatch, a malformed deflate stream, or an unexpected end
             * of input. All of them mean the same thing here - this is not a
             * readable DOCX - and none of them should escape into the upload
             * path, where they would become a 500 instead of a described 415.
             */
            return false;
        }
    }

    /** Little-endian 32-bit read. Callers bounds-check first. */
    private static int readIntLe(byte[] content, int offset) {
        return (content[offset] & 0xFF)
                | ((content[offset + 1] & 0xFF) << 8)
                | ((content[offset + 2] & 0xFF) << 16)
                | ((content[offset + 3] & 0xFF) << 24);
    }

    /** Little-endian 16-bit read. Callers bounds-check first. */
    private static int readShortLe(byte[] content, int offset) {
        return (content[offset] & 0xFF) | ((content[offset + 1] & 0xFF) << 8);
    }

    /**
     * Plain text has no magic number, so it is identified by ELIMINATION: valid
     * UTF-8, and no NUL byte.
     *
     * THE NUL CHECK IS THE SECURITY-RELEVANT HALF. Essentially every binary
     * format - ELF, PE, Mach-O, class files, most images - contains NUL bytes
     * early, and no legitimate text file contains one at all. Without this,
     * "rename your malware to .txt" would be a working upload.
     *
     * STRICT UTF-8 DECODING is the other half, and it has to be strict:
     * {@code new String(bytes, UTF_8)} silently replaces invalid sequences with
     * U+FFFD and therefore never fails, which would make the check decorative.
     * A CharsetDecoder configured to REPORT throws instead, so arbitrary binary
     * that happens to avoid NUL is still rejected.
     *
     * The consequence is that a Latin-1 or UTF-16 text file is refused. That is
     * the right trade for this project: everything downstream - extraction,
     * chunking, embedding - assumes UTF-8, so accepting another encoding here
     * would only defer the failure to a place with less context to explain it.
     */
    private static boolean looksLikePlainText(byte[] content) {
        /*
         * FAST PATH. Scanning raw bytes for NUL is far cheaper than decoding,
         * and essentially every binary file has one within the first few
         * hundred bytes - so most rejections cost a partial scan rather than a
         * full UTF-8 decode of a 10MB upload.
         *
         * Strictly redundant with isTextCharacter() below, which also rejects
         * NUL. Kept because it is the cheap early-out and because "no NUL
         * bytes" is a stated requirement of this detector, and a requirement
         * expressed only as a side effect of a wider rule is one somebody
         * removes by accident.
         */
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            text = decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return false;
        }

        /*
         * DECODING SUCCESSFULLY IS NOT ENOUGH, and this is the gap that let a
         * truncated archive be classified as text.
         *
         * `PK\003\004` - the first four bytes of every ZIP, and therefore of
         * every DOCX - is perfectly valid UTF-8: two letters followed by the
         * control characters ETX and EOT. It contains no NUL. A rule that
         * checked only "valid UTF-8, no NUL" therefore accepted the header
         * fragment of a truncated Word document as a plain text file, and the
         * same holds for the leading bytes of countless binary formats.
         *
         * Control characters are the discriminator. Text is characters a human
         * reads, plus the whitespace that lays them out; it is not ETX, EOT,
         * BEL or ESC.
         */
        for (int i = 0; i < text.length(); i++) {
            if (!isTextCharacter(text.charAt(i))) {
                return false;
            }
        }

        // A file of only whitespace has bytes but no text. Rejecting it here
        // means AI-2 never queues a document with nothing to extract.
        return !text.isBlank();
    }

    /**
     * Whether one decoded character may appear in a plain text document.
     *
     * THE RULE IS ABOUT CHARACTER CLASS, NOT LENGTH. A one-byte file containing
     * "P" is a legitimate, if unusual, text file and is accepted; a four-byte
     * file containing `PK\003\004` is a binary header and is not. Any minimum
     * length would be arbitrary, would reject real tiny files, and would still
     * accept a long binary that happened to avoid NUL.
     *
     * ALLOWED: TAB, LF, CR and FF - the four control characters that carry
     * layout rather than terminal semantics. FF is included because page breaks
     * appear in genuine plain-text documents, which is squarely the kind of
     * file this endpoint accepts.
     *
     * REJECTED: every other C0 control (0x00-0x1F), DEL (0x7F), and the C1
     * range (0x80-0x9F). C1 is worth naming: those code points only arise from
     * text that was really Latin-1 or Windows-1252 and got decoded as UTF-8, or
     * from binary - never from a document somebody typed.
     *
     * KNOWN, ACCEPTED COST: a DOS-era text file ending in SUB (0x1A), or one
     * containing BEL or ESC, is refused. Those are artefacts of terminals and
     * of a file format nobody is uploading to a legal platform in 2026, and
     * accepting them would mean accepting the leading bytes of most binary
     * formats along with them. The user gets a clear "only PDF, DOCX and TXT"
     * message rather than a document that later fails extraction.
     */
    private static boolean isTextCharacter(char c) {
        if (c == '\t' || c == '\n' || c == '\r' || c == '\f') {
            return true;
        }
        if (c < 0x20 || c == 0x7F) {
            return false;
        }
        return c < 0x80 || c > 0x9F;
    }
}
