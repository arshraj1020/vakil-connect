package com.arshraj.vakilconnect.ai.document.service;

import com.arshraj.vakilconnect.ai.document.dto.DocumentResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentSummaryResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Document upload and lifecycle, scoped to one user.
 *
 * EVERY METHOD TAKES THE CALLER'S EMAIL AS ITS FIRST PARAMETER, matching
 * AppointmentService and LawyerService. The value comes from
 * {@code Authentication.getName()} - which is set by JwtAuthenticationFilter
 * from a verified token signature - and never from a request body, a header a
 * client controls, or a path variable. A method that took a userId a caller
 * could supply would be an authorization bypass with a pleasant signature.
 *
 * OWNERSHIP IS ENFORCED IN THE SQL, not after it. Every query filters on the
 * owner in its WHERE clause, so a row belonging to somebody else is never
 * loaded and there is no object in memory for a later branch to forget to
 * check. See AiDocumentRepository.
 *
 * NO LLM IS INVOLVED IN ANY DECISION HERE, and none ever will be. Whether a
 * user may read or delete a document is settled by a foreign key and a WHERE
 * clause. A model's opinion is not an access-control mechanism.
 */
public interface AiDocumentService {

    /**
     * Validates and stores an uploaded file.
     *
     * The document is created in {@code PENDING}. Nothing extracts text at
     * AI-1, so it stays there.
     *
     * @throws com.arshraj.vakilconnect.common.exception.EmptyDocumentException
     *         the file has no bytes
     * @throws com.arshraj.vakilconnect.common.exception.InvalidDocumentNameException
     *         the filename is missing or nothing usable survives sanitising
     * @throws com.arshraj.vakilconnect.common.exception.DocumentTooLargeException
     *         the file exceeds the configured maximum
     * @throws com.arshraj.vakilconnect.common.exception.UnsupportedDocumentTypeException
     *         the extension is not allowed, or the bytes disagree with it
     */
    DocumentUploadResponse upload(String userEmail, MultipartFile file);

    /**
     * Metadata for one document the caller owns.
     *
     * @throws com.arshraj.vakilconnect.common.exception.ResourceNotFoundException
     *         if no such document exists OR it belongs to another user - the
     *         two are deliberately indistinguishable
     */
    DocumentResponse getOwnDocument(String userEmail, UUID documentId);

    /** The caller's own documents, newest first. Never anyone else's. */
    List<DocumentSummaryResponse> listOwnDocuments(String userEmail);

    /**
     * Deletes one document the caller owns.
     *
     * @throws com.arshraj.vakilconnect.common.exception.ResourceNotFoundException
     *         if no such document exists OR it belongs to another user
     */
    void deleteOwnDocument(String userEmail, UUID documentId);
}
