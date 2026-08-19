package com.arshraj.vakilconnect.ai.ingest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Documents that real parsers can actually read.
 *
 * HAND-BUILT RATHER THAN GENERATED WITH PDFBox/POI, deliberately. Both libraries
 * are on the classpath transitively through tika-parsers-standard-package, so
 * calling them would compile - but their APIs shift between majors (PDFBox 3
 * rewrote font construction), and pinning test fixtures to a TRANSITIVE
 * dependency's API means the tests break whenever Tika bumps it. Writing the
 * bytes directly depends on the FILE FORMATS, which do not change.
 *
 * It is also the point of the exercise: AI-1's DocumentFixtures produce files
 * that are structurally valid enough to be IDENTIFIED, which is all a magic-byte
 * detector needs. A parser needs more - a page tree, a content stream, an OPC
 * relationship graph - so extraction needs its own fixtures.
 */
final class IngestionFixtures {

    /** Appears in every generated document, so tests can assert on real output. */
    static final String PDF_MARKER = "TENANCY AGREEMENT";
    static final String DOCX_MARKER = "Clause 4 Security Deposit";

    private IngestionFixtures() {
    }

    /**
     * A structurally VALID minimal PDF containing {@link #PDF_MARKER}.
     *
     * BUILT PROGRAMMATICALLY WITH REAL BYTE OFFSETS, not written as a text
     * block. The first attempt was a literal, and it failed twice over:
     *
     *   1. It went through String.formatted(), where the leading `%PDF-1.4` is
     *      read as a format conversion - `%P` is not one, so the fixture threw
     *      UnknownFormatConversionException before any parser saw it. The
     *      trailing `%%EOF` had been escaped and the header had not, which is
     *      exactly the kind of asymmetry a literal invites. Nothing here goes
     *      through format() now, so a stray `%` in PDF syntax cannot bite.
     *
     *   2. It had no xref table and a hand-guessed /Length. PDFBox can rebuild
     *      a broken cross-reference table by scanning, but relying on the
     *      recovery path to make a fixture work means the test passes for a
     *      reason unrelated to what it claims to prove.
     *
     * So the offsets are recorded as the document is assembled and the xref is
     * emitted from them. Every entry is exactly 20 bytes, as the specification
     * requires. ISO-8859-1 throughout, because PDF syntax is byte-oriented and
     * character count must equal byte count for the offsets to be true.
     */
    static byte[] pdfWithText() {
        // No trailing newline, so /Length is exactly the data length.
        String contentStream = "BT /F1 18 Tf 72 720 Td (" + PDF_MARKER + ") Tj ET";

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R"
                        + " /Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + contentStream.length() + " >>\nstream\n"
                        + contentStream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");

        int[] offsets = new int[objects.size() + 1];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }

        int xrefOffset = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append('\n');
        // The free head entry, then one 20-byte entry per object.
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i <= objects.size(); i++) {
            // "\n" in a Java literal is always LF; %n would be the PLATFORM
            // separator, which on Windows is two bytes and would break the
            // 20-byte entry width the specification requires.
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }

        pdf.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * A minimal but genuinely parseable DOCX containing {@link #DOCX_MARKER}.
     *
     * FOUR PARTS, ALL REQUIRED. AI-1's fixture has only `word/document.xml`,
     * which satisfies a marker-entry check but NOT POI's OPCPackage: an OOXML
     * package is a relationship graph, and without `_rels/.rels` naming the main
     * document part there is no way to find the body. That fixture is therefore
     * a perfectly good "malformed DOCX" for the rejection tests, and a useless
     * one for extraction - which is why both exist.
     */
    static byte[] docxWithText() {
        return zip(
                entry("[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels"\
                         ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/word/document.xml"\
                         ContentType="application/vnd.openxmlformats-officedocument\
                        .wordprocessingml.document.main+xml"/>
                        </Types>
                        """),
                entry("_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships\
                         xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1"\
                         Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships\
                        /officeDocument" Target="word/document.xml"/>
                        </Relationships>
                        """),
                entry("word/_rels/document.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships\
                         xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                        """),
                entry("word/document.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <w:document\
                         xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                          <w:body>
                            <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                            <w:p><w:r><w:t>The deposit shall be refunded within 30 days.</w:t>\
                        </w:r></w:p>
                          </w:body>
                        </w:document>
                        """.formatted(DOCX_MARKER)));
    }

    /**
     * A realistic extract of legal prose: numbered clauses, sub-points,
     * headings, blank-line paragraph breaks and the punctuation that carries
     * meaning. Used by the normalizer and chunker tests, where the point is
     * that structure SURVIVES.
     */
    static String legalText() {
        return """
                RESIDENTIAL TENANCY AGREEMENT

                1. DEFINITIONS

                1.1 "Premises" means the property at 14 Nariman Point, Mumbai.
                1.2 "Term" means the period of 11 months commencing 1 April 2026.

                2. RENT

                2.1 The Tenant shall pay Rs. 45,000 per month, in advance, on or before
                the 5th day of each month; time being of the essence.
                2.2 Late payment shall attract interest at 1.5% per month, provided that
                no interest shall accrue where the delay is attributable to the Landlord.

                3. SECURITY DEPOSIT

                3.1 The Tenant has paid Rs. 2,70,000 as an interest-free deposit.
                3.2 The deposit shall be refunded within 30 days of vacant possession,
                less any sums lawfully deducted under clause 5.3.
                """;
    }

    /**
     * Filler of roughly {@code characters} length in which every region is
     * TEXTUALLY DISTINCT, because each token carries an incrementing number.
     *
     * Needed because the chunker deduplicates by content. Homogeneous filler
     * like {@code "a".repeat(2400)} produces chunks that are
     * character-for-character identical, so they collapse to one - correct
     * behaviour, but useless for asserting that splitting happened. Numbered
     * tokens make each chunk unique while still splitting on word boundaries.
     */
    static String distinctFiller(int characters) {
        StringBuilder text = new StringBuilder(characters + 16);
        int token = 0;
        while (text.length() < characters) {
            text.append("clause").append(token++).append(' ');
        }
        return text.toString().strip();
    }

    /** Text guaranteed to exceed one 1200-character chunk. */
    static String longLegalText(int approximateCharacters) {
        StringBuilder text = new StringBuilder();
        int clause = 1;
        while (text.length() < approximateCharacters) {
            text.append(clause).append(". CLAUSE ").append(clause).append("\n\n")
                    .append(clause).append(".1 The parties agree that the obligations set out ")
                    .append("in this clause shall survive termination of this agreement, ")
                    .append("save where expressly stated otherwise in writing.\n\n");
            clause++;
        }
        return text.toString();
    }

    // ------------------------------------------------------------- plumbing

    private record ZipFileEntry(String name, String content) {
    }

    private static ZipFileEntry entry(String name, String content) {
        return new ZipFileEntry(name, content);
    }

    private static byte[] zip(ZipFileEntry... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipFileEntry e : entries) {
                zip.putNextEntry(new ZipEntry(e.name()));
                zip.write(e.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
