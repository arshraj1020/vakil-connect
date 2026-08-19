package com.arshraj.vakilconnect.ai.embedding;

/**
 * A PERMANENT embedding failure. Retrying is pointless.
 *
 * Thrown for:
 *
 *   - 4xx other than 429 - a malformed request, or a model identifier the
 *     server does not have. Every one fails identically on the next attempt.
 *
 *   - A RESPONSE OF THE WRONG SHAPE OR WIDTH. This is the case worth naming: a
 *     vector whose length does not match the configured dimension cannot be
 *     stored, because `vector(768)` rejects it - and if it somehow were stored,
 *     every future similarity comparison against it would be meaningless.
 *     Catching it at the client turns a confusing database constraint error
 *     into "the configured model does not produce the configured dimension".
 */
public class PermanentEmbeddingException extends EmbeddingException {

    public PermanentEmbeddingException(String message) {
        super(message);
    }

    public PermanentEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
