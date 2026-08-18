package com.arshraj.vakilconnect.ai.document.repository;

import com.arshraj.vakilconnect.ai.document.dto.DocumentResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentSummaryResponse;
import com.arshraj.vakilconnect.ai.document.entity.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link AiDocument}.
 *
 * TWO PROPERTIES HOLD ACROSS EVERY METHOD HERE, and they are the reason this
 * interface is written with explicit JPQL rather than derived query names.
 *
 * 1. NO READ EVER SELECTS `content`.
 *
 *    A stored document is up to several megabytes. `findById` would drag all of
 *    it into the persistence context to answer "what is this file called", and
 *    a list of twenty documents would pull twenty files into heap to render a
 *    table. JPA's usual answer - {@code @Basic(fetch = LAZY)} on the byte[] -
 *    is SILENTLY IGNORED without the Hibernate bytecode enhancer, which this
 *    build does not configure, so writing it would be a comforting lie.
 *
 *    Constructor expressions make it structural instead of aspirational: the
 *    generated SQL literally lists the columns below, and `content` is not
 *    among them. It cannot regress without someone editing the SELECT clause.
 *
 *    The consequence is that {@code JpaRepository}'s inherited finders -
 *    findById, findAll - MUST NOT be used for reads in this feature. They are
 *    inherited and cannot be removed; the methods here are the supported way.
 *
 * 2. OWNERSHIP IS IN THE QUERY, NOT AFTER IT.
 *
 *    Every method that touches a specific document takes the owner's id and
 *    filters on it in the WHERE clause. A row belonging to somebody else is
 *    never loaded, so there is no object in memory for a later {@code if}
 *    statement to forget to check. "Load, then compare owner" is the same
 *    mechanism with one more chance to get it wrong, and it is the shape that
 *    produces cross-tenant leaks when a branch is refactored.
 *
 * DTO IMPORTS IN A REPOSITORY are a deliberate trade. It couples this interface
 * to the response shapes, which is a real cost; the alternative - projecting to
 * a repository-local record and remapping in the service - buys layering purity
 * and adds a type whose only job is to be copied field-for-field. Given that
 * property 1 above is a security-relevant invariant, expressing it in one
 * unambiguous place won.
 */
public interface AiDocumentRepository extends JpaRepository<AiDocument, UUID> {

    /**
     * Full metadata for one document the caller owns.
     *
     * Returns empty both when the id does not exist AND when it belongs to
     * somebody else - the two are indistinguishable to the caller by design.
     * The service turns either into a 404, so probing ids reveals nothing about
     * what other users have uploaded.
     */
    @Query("""
            SELECT new com.arshraj.vakilconnect.ai.document.dto.DocumentResponse(
                       d.id, d.filename, d.contentType, d.sizeBytes, d.sha256,
                       d.status, d.failureReason, d.createdAt, d.updatedAt)
              FROM AiDocument d
             WHERE d.id = :documentId
               AND d.user.id = :userId
            """)
    Optional<DocumentResponse> findMetadataByIdAndOwner(@Param("documentId") UUID documentId,
                                                        @Param("userId") UUID userId);

    /**
     * The caller's own documents, newest first.
     *
     * Matches ix_ai_documents_user_created exactly, so the ordering is an index
     * scan rather than a sort.
     *
     * UNPAGED, and that is a decision with a limit on it: a portfolio user has
     * a handful of documents, and pagination would add a contract the frontend
     * does not exist to consume yet. It becomes wrong the moment a user can
     * accumulate hundreds - the projection keeps each row tiny, so the ceiling
     * is high, but it is a ceiling.
     */
    @Query("""
            SELECT new com.arshraj.vakilconnect.ai.document.dto.DocumentSummaryResponse(
                       d.id, d.filename, d.contentType, d.sizeBytes,
                       d.status, d.createdAt)
              FROM AiDocument d
             WHERE d.user.id = :userId
             ORDER BY d.createdAt DESC
            """)
    List<DocumentSummaryResponse> findAllByOwner(@Param("userId") UUID userId);

    /**
     * Deletes one document, but only if the caller owns it.
     *
     * THE RETURN VALUE IS THE DECISION. One row means it was deleted; zero
     * means it did not exist or was not theirs, and the service maps that to a
     * 404. Callers must branch on the count and must NOT re-read the row to
     * find out whether they won - that would reintroduce a check-then-act
     * window and, worse, invite a version that loads the entity (and its
     * megabytes) purely to decide whether to delete it.
     *
     * A bulk DELETE also means the file contents are never read into the
     * application to be thrown away, which is the whole cost of the naive
     * `findById().ifPresent(repo::delete)` shape.
     *
     * clearAutomatically: a bulk delete bypasses the persistence context, so a
     * document already loaded in this transaction would still appear present.
     * flushAutomatically: a pending insert must reach the database first, or a
     * document created moments ago in the same transaction would be invisible
     * to this statement.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM AiDocument d
             WHERE d.id = :documentId
               AND d.user.id = :userId
            """)
    int deleteByIdAndOwner(@Param("documentId") UUID documentId,
                           @Param("userId") UUID userId);

    /**
     * How many documents one user owns.
     *
     * Exists for tests and for the per-user quota AI-2 will need before it
     * starts spending CPU on extraction. Counting in SQL rather than
     * {@code findAllByOwner().size()} keeps it O(index) and, again, loads no
     * rows.
     */
    long countByUserId(UUID userId);
}
