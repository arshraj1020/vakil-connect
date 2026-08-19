package com.arshraj.vakilconnect.ai.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Byte payloads for upload tests.
 *
 * REAL BYTES, NOT PLACEHOLDER STRINGS. The upload path identifies files by
 * inspecting their contents, so a test that posted {@code "fake pdf".getBytes()}
 * with a .pdf name would exercise the REJECTION path while claiming to test the
 * happy one - and would keep passing if the detector were deleted entirely.
 * Every fixture here satisfies the check it is meant to satisfy, for the same
 * reason a real file would.
 *
 * NO FILES ON DISK. Test resources would put binary blobs in the repository and
 * make "why does this fixture pass" a question answerable only by a hex editor.
 * Building them in code means the reason each one is valid is written next to
 * it.
 *
 * PUBLIC SINCE AI-2, AND ONLY pdf()/docx(). Those two are structurally valid
 * enough to be IDENTIFIED but not to be PARSED - no page tree, no content
 * stream, no OPC relationship graph - which makes them exactly the malformed
 * documents TikaDocumentTextExtractorTest needs. Duplicating them into the
 * ingest package would have been thirty lines of copy that could silently drift
 * from the originals these tests are meant to mirror.
 */
public final class DocumentFixtures {

    private DocumentFixtures() {
    }

    /**
     * A minimal PDF.
     *
     * The detector keys on the `%PDF-` prefix, which the specification requires
     * at the start of the file, so this is genuinely enough to be identified as
     * one. The trailer and EOF marker are included so the fixture is a
     * structurally sensible document rather than five magic bytes and noise.
     */
    public static byte[] pdf() {
        return """
                %PDF-1.7
                1 0 obj
                <</Type/Catalog/Pages 2 0 R>>
                endobj
                trailer
                <</Root 1 0 R>>
                %%EOF
                """.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A minimal DOCX: a real ZIP archive containing `word/document.xml`.
     *
     * BOTH HALVES ARE NECESSARY. The ZIP magic alone would also match a JAR, an
     * XLSX, an EPUB or an APK, which is exactly why the detector opens the
     * archive and looks for the OOXML word-processing marker entry. A fixture
     * that only produced ZIP bytes would pass a weaker detector and silently
     * stop testing the thing that matters.
     */
    public static byte[] docx() {
        return zipContaining(
                entry("[Content_Types].xml",
                        "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats"
                                + ".org/package/2006/content-types\"/>"),
                entry("word/document.xml",
                        "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas"
                                + ".openxmlformats.org/wordprocessingml/2006/main\">"
                                + "<w:body><w:p><w:r><w:t>Tenancy agreement</w:t></w:r>"
                                + "</w:p></w:body></w:document>"));
    }

    /**
     * A valid ZIP that is NOT a DOCX - no `word/document.xml`.
     *
     * Stands in for the whole family of archive formats that share ZIP's magic
     * number. Renaming any of them to .docx must not get them stored.
     */
    static byte[] zipThatIsNotDocx() {
        return zipContaining(
                entry("[Content_Types].xml", "<?xml version=\"1.0\"?><Types/>"),
                entry("xl/workbook.xml", "<workbook/>"));
    }

    /**
     * A structurally complete archive whose `word/document.xml` DATA is
     * corrupted - the case the End of Central Directory check alone cannot
     * catch.
     *
     * The EOCD and the central directory are left untouched, so the archive
     * still declares the marker entry and still looks intact to any check that
     * stops at the index. Only decompressing the entry reveals the damage, via
     * a broken deflate stream or a CRC mismatch.
     *
     * The corrupted range sits immediately before the central directory, which
     * is the tail of the LAST entry written - `word/document.xml`. Depending on
     * how ZipOutputStream chose to frame it, those bytes are either the end of
     * the compressed data or the entry's data descriptor (which carries the
     * CRC). Either way the entry no longer verifies, which is the point.
     */
    static byte[] docxWithCorruptedEntryData() {
        /*
         * A LARGE body, deliberately. With a tiny document the whole archive is
         * a few hundred bytes and the central directory is most of it, so
         * "corrupt the bytes just before the directory" would risk landing on a
         * local header instead of on entry data. Padding the body makes the
         * data region dominate and the target unambiguous.
         */
        String body = "<?xml version=\"1.0\"?><w:document><w:body><w:p><w:r><w:t>"
                + "clause ".repeat(600)
                + "</w:t></w:r></w:p></w:body></w:document>";

        byte[] archive = zipContaining(
                entry("[Content_Types].xml", "<?xml version=\"1.0\"?><Types/>"),
                entry("word/document.xml", body));

        int eocd = findEndOfCentralDirectory(archive);
        int directoryOffset = readIntLe(archive, eocd + 16);

        if (directoryOffset < 64) {
            throw new IllegalStateException(
                    "fixture is broken: central directory at " + directoryOffset
                            + " leaves no entry data to corrupt");
        }

        // Invert a run of bytes at the very end of the entry region.
        for (int i = directoryOffset - 32; i < directoryOffset - 4; i++) {
            archive[i] = (byte) ~archive[i];
        }
        return archive;
    }

    /**
     * ZIP magic followed by binary noise - no central directory, no EOCD.
     *
     * What a genuinely corrupt or hand-forged archive looks like. NUL bytes are
     * included on purpose: without them the payload would decode as valid UTF-8
     * and be correctly classified as plain text rather than as nothing, which
     * would make an "is it empty" assertion test the wrong thing.
     */
    static byte[] invalidZip() {
        byte[] content = new byte[256];
        content[0] = 0x50;
        content[1] = 0x4B;
        content[2] = 0x03;
        content[3] = 0x04;
        for (int i = 4; i < content.length; i++) {
            // Alternating noise and NUL: binary, and nothing a ZIP reader can use.
            content[i] = (i % 2 == 0) ? (byte) 0x00 : (byte) (i & 0x7F);
        }
        return content;
    }

    static byte[] txt() {
        return txt("This is a tenancy agreement between the landlord and the tenant.");
    }

    static byte[] txt(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Binary content with NUL bytes - what an executable, an image or a
     * compiled class looks like to the detector.
     *
     * The leading bytes are ELF's magic number (0x7F 'E' 'L' 'F'), so this is
     * not merely "some bytes": it is the shape of the exact file somebody would
     * rename to .pdf or .txt to get it past an extension check.
     */
    static byte[] binaryWithNulBytes() {
        byte[] content = new byte[256];
        content[0] = 0x7F;
        content[1] = 'E';
        content[2] = 'L';
        content[3] = 'F';
        // The remaining bytes stay zero: NUL is the signal no text file carries.
        return content;
    }

    /**
     * A payload larger than the test profile's 64KB limit.
     *
     * Valid UTF-8 text, so if the size check were removed this would be stored
     * successfully rather than failing for an unrelated reason - which is what
     * makes the oversize test prove the limit rather than prove the detector.
     */
    static byte[] oversizedText(int bytes) {
        return "a".repeat(bytes).getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- plumbing

    private record ZipFileEntry(String name, String content) {
    }

    private static ZipFileEntry entry(String name, String content) {
        return new ZipFileEntry(name, content);
    }

    /**
     * Locates the End of Central Directory record by scanning backwards.
     *
     * A deliberate, small duplication of what the detector does. The fixture
     * has to know where the central directory starts in order to corrupt bytes
     * BEFORE it, and hardcoding an offset would silently drift the moment
     * ZipOutputStream changed how it frames an entry - producing a fixture that
     * quietly stopped testing corruption while still passing.
     */
    private static int findEndOfCentralDirectory(byte[] archive) {
        for (int i = archive.length - 22; i >= 0; i--) {
            if (readIntLe(archive, i) == 0x06054b50) {
                return i;
            }
        }
        throw new IllegalStateException("fixture is broken: no EOCD in a freshly built archive");
    }

    private static int readIntLe(byte[] content, int offset) {
        return (content[offset] & 0xFF)
                | ((content[offset + 1] & 0xFF) << 8)
                | ((content[offset + 2] & 0xFF) << 16)
                | ((content[offset + 3] & 0xFF) << 24);
    }

    private static byte[] zipContaining(ZipFileEntry... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipFileEntry e : entries) {
                zip.putNextEntry(new ZipEntry(e.name()));
                zip.write(e.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            // In-memory stream; an IOException here is impossible in practice
            // and would mean the fixture, not the code under test, is broken.
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
