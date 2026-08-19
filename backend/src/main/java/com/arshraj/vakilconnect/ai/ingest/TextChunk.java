package com.arshraj.vakilconnect.ai.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One piece of a document, before it is embedded or stored.
 *
 * A VALUE OBJECT WITH NO IDENTITY AND NO DOCUMENT REFERENCE. Chunking is a pure
 * function of text, so keeping it free of persistence concerns is what lets it
 * be tested without a database - and what stops a chunker ever reaching for a
 * repository.
 */
public record TextChunk(int index, String content, String contentHash, int charCount) {

    public TextChunk {
        if (index < 0) {
            throw new IllegalArgumentException("chunk index must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("a chunk must have content");
        }
    }

    /** Builds a chunk, computing its hash and length. */
    public static TextChunk of(int index, String content) {
        String trimmed = content.strip();
        return new TextChunk(index, trimmed, sha256Hex(trimmed), trimmed.length());
    }

    /**
     * Hex SHA-256, matching ck_ai_document_chunks_hash_format.
     *
     * UNSALTED ON PURPOSE, unlike TokenHasher which HMACs because it protects a
     * secret. This hash answers "is this the same text as before", so it must
     * be reproducible - a per-instance salt would make every reprocessing look
     * like a change.
     */
    private static String sha256Hex(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * REDACTED. A record prints every component, and this one holds a paragraph
     * of the user's legal document. The index, hash and length are everything a
     * log line legitimately needs, and the hash is what makes two runs
     * comparable without reproducing the text.
     */
    @Override
    public String toString() {
        return "TextChunk{index=" + index + ", chars=" + charCount
                + ", hash=" + contentHash.substring(0, 12) + "..., content=<not shown>}";
    }
}
