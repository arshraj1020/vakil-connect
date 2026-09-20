package com.arshraj.vakilconnect.ai.compare;

import java.util.UUID;

/**
 * Compares two documents the caller owns.
 *
 * Takes the caller's EMAIL first, like every other AI service in this
 * codebase - resolved from {@code Authentication.getName()}. There is no
 * parameter through which a caller can name an owner for either document.
 */
public interface DocumentComparisonService {

    /**
     * @throws com.arshraj.vakilconnect.common.exception.ResourceNotFoundException
     *         if the account no longer exists, or EITHER document does not
     *         exist or belongs to somebody else - indistinguishable by design
     * @throws IllegalArgumentException
     *         if the same document id is given twice
     * @throws com.arshraj.vakilconnect.common.exception.DocumentNotAnalyzableException
     *         if either document has not been through AI-2's pipeline
     * @throws com.arshraj.vakilconnect.common.exception.DocumentProcessingConflictException
     *         if either document is being processed right now
     * @throws com.arshraj.vakilconnect.common.exception.AiAnswerUnavailableException
     *         if the model is unreachable, or its reply is not usable
     *         structured output
     */
    DocumentComparison compare(String userEmail, UUID documentId, UUID compareToDocumentId);
}
