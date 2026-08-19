package com.arshraj.vakilconnect.ai.rag;

/**
 * Answers a question from the caller's own documents.
 *
 * Takes the caller's EMAIL first, like every other service here - the value
 * comes from {@code Authentication.getName()}, set by JwtAuthenticationFilter
 * from a verified token signature.
 */
public interface RagService {

    /**
     * @throws com.arshraj.vakilconnect.common.exception.ResourceNotFoundException
     *         if the account no longer exists
     * @throws com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException
     *         if the model is unreachable or returns nothing usable
     */
    RagAnswer ask(String userEmail, String question);
}
