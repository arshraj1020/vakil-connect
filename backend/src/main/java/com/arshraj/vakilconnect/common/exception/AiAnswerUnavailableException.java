package com.arshraj.vakilconnect.common.exception;

/**
 * The assistant could not produce an answer. Maps to HTTP 503 with code
 * AI_ANSWER_UNAVAILABLE.
 *
 * 503 SERVICE UNAVAILABLE, matching DocumentEmbeddingException. The
 * overwhelmingly common cause is that the local Ollama server is not running or
 * the model was never pulled - a DEPENDENCY being down, not a bad request and
 * not a broken application. 503 is the one status that tells a client "try
 * again later" truthfully.
 *
 * NOT USED FOR "the documents do not answer this". That is a successful
 * response with {@code grounded: false}, because it is an ANSWER - the honest
 * one - rather than a failure. Conflating the two would make a working system
 * look broken every time a user asked about something they had not uploaded.
 *
 * THE MESSAGE NEVER CARRIES THE QUESTION, THE CONTEXT OR THE MODEL'S OUTPUT.
 * All three are user document content or derived from it.
 */
public class AiAnswerUnavailableException extends RuntimeException {

    public static final String CODE = "AI_ANSWER_UNAVAILABLE";

    public AiAnswerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
