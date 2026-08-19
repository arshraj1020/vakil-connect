package com.arshraj.vakilconnect.ai.ingest;

/**
 * Gets readable text out of a stored document's bytes.
 *
 * AN INTERFACE BECAUSE THE PARSER IS AN IMPLEMENTATION DETAIL. Tika is the
 * right tool today; if a format ever needs a specialised parser, or if the
 * standard package's weight becomes a problem, the replacement is one class.
 *
 * TAKES BYTES AND A CONTENT TYPE, NOT A DOCUMENT ID. Deliberate: an extractor
 * that could load a document could load the WRONG document. Ownership is
 * resolved before anything reaches here, and this layer has no repository and
 * no way to reach one.
 */
public interface DocumentTextExtractor {

    /**
     * @param content     the stored bytes, already size-bounded by AI-1
     * @param contentType the SERVER-DETECTED type stored in ai_documents -
     *                    never a client-supplied header
     * @return non-blank extracted text
     * @throws com.arshraj.vakilconnect.common.exception.DocumentExtractionException
     *         if the document cannot be parsed, or yields no text
     */
    String extract(byte[] content, String contentType);
}
