package com.arshraj.vakilconnect.ai.ingest;

import com.arshraj.vakilconnect.ai.document.service.DocumentContentTypeDetector;
import com.arshraj.vakilconnect.common.exception.DocumentExtractionException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Extracts text with Apache Tika, except for plain text, which needs no parser.
 *
 * TXT BYPASSES TIKA ENTIRELY. AI-1's detector already proved those bytes are
 * strict UTF-8 containing no NUL and no control characters other than layout
 * whitespace - so the text is simply the decoded bytes. Routing it through a
 * parser would add a round trip to re-derive a fact already established, and
 * would introduce a charset-guessing step that could only make the answer
 * worse.
 *
 * NO OCR, AND NO ATTEMPT AT IT. A scanned PDF is an image; Tika returns little
 * or nothing, and the document is rejected as unextractable with a message
 * saying so. Adding Tesseract would mean a native binary, a large dependency
 * and a slow path - a real feature, and out of scope for AI-2.
 */
@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentTextExtractor.class);

    /**
     * Tika's own default caps extracted text at 100,000 characters, silently.
     *
     * That is far too low here: a 40-page agreement exceeds it easily, and the
     * failure mode is the worst available - a successful extraction that is
     * quietly missing its second half, which then never appears in retrieval
     * and gives no signal that anything went wrong.
     *
     * -1 disables the limit. The real bound is AI-1's upload cap: a 10MB
     * document cannot decompress to unbounded text in any format this
     * application accepts.
     */
    private static final int NO_WRITE_LIMIT = -1;

    @Override
    public String extract(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new DocumentExtractionException("The document is empty.");
        }

        String text = DocumentContentTypeDetector.TXT.equals(contentType)
                ? new String(content, StandardCharsets.UTF_8)
                : parseWithTika(content, contentType);

        /*
         * A DOCUMENT WITH NO TEXT IS A FAILURE, NOT AN EMPTY SUCCESS.
         *
         * The realistic cause is a scanned PDF - pages of images with no text
         * layer. Letting it through would create a document in READY with zero
         * chunks, which looks healthy in the API and silently contributes
         * nothing to any answer. Failing here means the user is told the file
         * needs OCR.
         */
        if (text == null || text.isBlank()) {
            throw new DocumentExtractionException(
                    "No text could be read from this document. "
                            + "Scanned or image-only files are not supported.");
        }

        // Size and type only. NEVER a fragment of the text.
        log.debug("Extracted {} characters from a {} document", text.length(), contentType);

        return text;
    }

    /**
     * AutoDetectParser rather than a format-specific one.
     *
     * The content type is passed as a HINT via metadata, but Tika still
     * confirms it from the bytes. That matters because the stored type came
     * from AI-1's own detector rather than from the client - two independent
     * agreements about what a file is are better than one, and a disagreement
     * shows up as a parse failure rather than as garbage text.
     */
    private String parseWithTika(byte[] content, String contentType) {
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(NO_WRITE_LIMIT);

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, contentType);

        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            parser.parse(stream, handler, metadata, new ParseContext());
            return handler.toString();

        } catch (Exception e) {
            /*
             * EVERYTHING BECOMES ONE TYPED, SAFE EXCEPTION.
             *
             * Tika throws TikaException, SAXException, IOException and, from
             * deeper parsers, a long tail of PDFBox and POI runtime
             * exceptions - many of which embed document content or byte
             * offsets in their messages. None of that may reach a user or a
             * log, so the cause is attached for the stack trace but the
             * MESSAGE is fixed here and the parser's own message is never
             * interpolated into it.
             */
            log.warn("Tika could not parse a {} document ({})",
                    contentType, e.getClass().getSimpleName());

            throw new DocumentExtractionException(
                    "This document could not be read. It may be corrupt or password-protected.",
                    e);
        }
    }
}
