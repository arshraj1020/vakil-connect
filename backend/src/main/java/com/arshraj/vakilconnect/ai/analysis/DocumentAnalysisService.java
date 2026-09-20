package com.arshraj.vakilconnect.ai.analysis;

import java.util.UUID;

/**
 * Produces a structured analysis of ONE document the caller owns.
 *
 * Takes the caller's EMAIL first, like every other service in this package -
 * the value comes from {@code Authentication.getName()}, set by
 * JwtAuthenticationFilter from a verified token signature. There is no
 * parameter through which a caller can name an owner.
 */
public interface DocumentAnalysisService {

    /**
     * @throws com.arshraj.vakilconnect.common.exception.ResourceNotFoundException
     *         if the account no longer exists, or the document does not exist
     *         OR belongs to somebody else - the last two deliberately
     *         indistinguishable
     * @throws com.arshraj.vakilconnect.common.exception.DocumentNotAnalyzableException
     *         if the document has not been through AI-2's pipeline
     * @throws com.arshraj.vakilconnect.common.exception.DocumentProcessingConflictException
     *         if the document is being processed right now
     * @throws com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException
     *         if the model is unreachable, or its reply is not usable
     *         structured output
     */
    DocumentAnalysis analyze(String userEmail, UUID documentId);
}
