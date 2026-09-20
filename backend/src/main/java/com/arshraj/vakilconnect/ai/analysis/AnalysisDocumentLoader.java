package com.arshraj.vakilconnect.ai.analysis;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads one document for analysis, scoped to its owner.
 *
 * AN INTERFACE, MIRRORING AI-3's DocumentRetriever, and for the same two
 * reasons. It keeps the storage decision out of the service - this
 * implementation reads AI-2's chunk table, and a later one could read a cached
 * full text without the pipeline noticing. More usefully, it lets
 * DocumentAnalysisServiceTest stage a document without a database, so the
 * behaviour that matters there - what the service does with each STATE - is
 * tested as control flow rather than as a fixture-loading exercise.
 *
 * THE OWNER ID IS A PARAMETER, NOT A FILTER APPLIED AFTERWARDS. Every
 * implementation must enforce it inside the query, so a document belonging to
 * somebody else is never loaded and there is no object in memory for a later
 * branch to forget to check.
 */
public interface AnalysisDocumentLoader {

    /**
     * @param ownerId the AUTHENTICATED caller's id, resolved from the security
     *                context. Never a value supplied by a client or a model.
     * @return empty when the document does not exist OR is not this user's -
     *         indistinguishable by design, so the caller can only produce a 404
     */
    Optional<AnalysisDocument> load(UUID documentId, UUID ownerId);
}
