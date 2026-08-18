package com.arshraj.vakilconnect.ai.document.service;

import com.arshraj.vakilconnect.ai.document.config.AiDocumentProperties;
import com.arshraj.vakilconnect.ai.document.dto.DocumentResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentSummaryResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentUploadResponse;
import com.arshraj.vakilconnect.ai.document.entity.AiDocument;
import com.arshraj.vakilconnect.ai.document.entity.AiDocumentStatus;
import com.arshraj.vakilconnect.ai.document.repository.AiDocumentRepository;
import com.arshraj.vakilconnect.common.exception.DocumentTooLargeException;
import com.arshraj.vakilconnect.common.exception.EmptyDocumentException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.common.exception.UnsupportedDocumentTypeException;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The only class that writes {@code ai_documents}.
 *
 * NOTHING HERE LOGS FILE CONTENT. Not the bytes, not a decoded prefix, not a
 * "first 100 characters" debugging aid. Log lines carry the document id, the
 * sanitised filename, the detected type and the size - which is everything an
 * operator needs to trace an upload and nothing that discloses what the
 * document says. A user's contract or medical record must not become searchable
 * in a log aggregator because somebody wanted a nicer debug line.
 */
@Service
public class AiDocumentServiceImpl implements AiDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentServiceImpl.class);

    private final AiDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AiDocumentProperties properties;

    public AiDocumentServiceImpl(AiDocumentRepository documentRepository,
                                 UserRepository userRepository,
                                 AiDocumentProperties properties) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public DocumentUploadResponse upload(String userEmail, MultipartFile file) {
        User owner = requireUser(userEmail);

        /*
         * VALIDATION ORDER IS DELIBERATE, cheapest and most certain first.
         *
         * Emptiness and size come before reading the bytes, because both are
         * answerable from the multipart metadata alone - so an 80 MB upload is
         * refused without ever being copied into heap. Only then is the content
         * read and inspected. Reversing this would mean the size check ran
         * after the thing it was protecting against had already happened.
         */
        if (file == null || file.isEmpty()) {
            throw new EmptyDocumentException();
        }

        long declaredSize = file.getSize();
        if (declaredSize > properties.maxFileSizeBytes()) {
            throw new DocumentTooLargeException(properties.maxFileSizeBytes());
        }

        // Throws if the name is missing or nothing usable survives.
        String filename = DocumentFilenameSanitizer.sanitize(file.getOriginalFilename());
        String extension = DocumentFilenameSanitizer.extensionOf(filename);

        if (!DocumentContentTypeDetector.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedDocumentTypeException();
        }

        byte[] content = readBytes(file);

        /*
         * RE-CHECK THE SIZE FROM THE ACTUAL BYTES.
         *
         * MultipartFile.getSize() reports what the container parsed, and the
         * check above used it. This one uses what was really read. They agree
         * in every normal case; asserting on the real length means a
         * disagreement - a truncated stream, a resolver quirk, a future change
         * to how the part is buffered - cannot store something larger than the
         * limit. The declared value is a claim; content.length is a fact.
         */
        if (content.length == 0) {
            throw new EmptyDocumentException();
        }
        if (content.length > properties.maxFileSizeBytes()) {
            throw new DocumentTooLargeException(properties.maxFileSizeBytes());
        }

        /*
         * THE TYPE CHECK THAT ACTUALLY MATTERS.
         *
         * The extension says what the user CLAIMS the file is; detect() says
         * what the bytes ARE. Requiring agreement catches both realistic
         * attacks in one comparison: an executable renamed to .pdf, and a real
         * PDF disguised as .txt.
         *
         * The client's Content-Type header is never consulted - not here, not
         * anywhere. It is attacker-controlled, so it is not evidence.
         */
        String detectedType = DocumentContentTypeDetector.detect(content)
                .orElseThrow(UnsupportedDocumentTypeException::new);

        String expectedType = DocumentContentTypeDetector.expectedTypeForExtension(extension)
                .orElseThrow(UnsupportedDocumentTypeException::new);

        if (!detectedType.equals(expectedType)) {
            throw new UnsupportedDocumentTypeException();
        }

        AiDocument document = new AiDocument();
        document.setUser(owner);
        document.setFilename(filename);
        // The DETECTED type is stored. The client's claim is discarded.
        document.setContentType(detectedType);
        document.setSizeBytes(content.length);
        document.setSha256(sha256Hex(content));
        document.setContent(content);
        document.setStatus(AiDocumentStatus.PENDING);

        AiDocument saved = documentRepository.save(document);

        // Metadata only. Never the content, never a fragment of it.
        log.info("Stored document {} for user {} ({}, {} bytes, status {})",
                saved.getId(), owner.getId(), detectedType, content.length, saved.getStatus());

        return new DocumentUploadResponse(
                saved.getId(),
                saved.getFilename(),
                saved.getContentType(),
                saved.getSizeBytes(),
                saved.getSha256(),
                saved.getStatus(),
                saved.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getOwnDocument(String userEmail, UUID documentId) {
        User owner = requireUser(userEmail);

        /*
         * 404 FOR SOMEBODY ELSE'S DOCUMENT, NOT 403.
         *
         * A 403 would confirm the id exists, which turns this endpoint into an
         * oracle: iterate ids, and the status code alone maps out which
         * documents other users hold. Since UUIDv4 ids are unguessable the
         * practical risk is low, but the distinction costs nothing and the
         * caller is entitled to exactly the same information either way -
         * "there is no such document, as far as you are concerned".
         *
         * The repository does not load the row at all, so this is not a check
         * performed on data we fetched; the data was never ours to fetch.
         */
        return documentRepository.findMetadataByIdAndOwner(documentId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> listOwnDocuments(String userEmail) {
        User owner = requireUser(userEmail);
        return documentRepository.findAllByOwner(owner.getId());
    }

    @Override
    @Transactional
    public void deleteOwnDocument(String userEmail, UUID documentId) {
        User owner = requireUser(userEmail);

        /*
         * THE AFFECTED-ROW COUNT IS THE DECISION.
         *
         * One row deleted means it existed and was theirs. Zero means it did
         * not exist, or it belonged to someone else - indistinguishable, and
         * mapped to the same 404 as the read path for the same reason.
         *
         * Deliberately NOT "load it, check the owner, then delete": that shape
         * has a window between the check and the act, and it would pull the
         * document's full contents into heap purely to decide to throw them
         * away.
         */
        int deleted = documentRepository.deleteByIdAndOwner(documentId, owner.getId());

        if (deleted == 0) {
            throw new ResourceNotFoundException("Document not found");
        }

        log.info("Deleted document {} for user {}", documentId, owner.getId());
    }

    // ------------------------------------------------------------- helpers

    /**
     * The authenticated caller's row.
     *
     * The email arrives from {@code Authentication.getName()}, which
     * JwtAuthenticationFilter populated from a signature-verified token. A
     * missing row therefore means the account was deleted between the token
     * being issued and this request - rare, and correctly a 404 rather than a
     * 500.
     */
    private User requireUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Reads the part into memory.
     *
     * FULLY IN MEMORY, AND BOUNDED BY THE CHECKS ABOVE. That is acceptable
     * precisely because the destination is a `bytea` column, which has to be
     * materialised anyway - streaming to the database would not avoid the
     * allocation. It is also why the size limit is not a nicety: without it
     * this line is an out-of-memory vector.
     *
     * An IOException here is a broken request stream, not a server fault, so it
     * becomes an empty-document error rather than a 500. The cause is logged
     * WITHOUT the partial bytes.
     */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("Could not read uploaded part ({} bytes declared): {}",
                    file.getSize(), e.getMessage());
            throw new EmptyDocumentException();
        }
    }

    /**
     * Lowercase hex SHA-256, matching ck_ai_documents_sha256_format.
     *
     * SHA-256 IS AN INTEGRITY CHECK HERE, NOT A SECURITY BOUNDARY. It answers
     * "are these the bytes that were uploaded" and gives AI-2 a cheap identity
     * for deduplication. It is deliberately unsalted and unpeppered - unlike
     * TokenHasher, which HMACs because it protects a secret. A document hash
     * protects nothing; making it non-reproducible would only stop the owner
     * verifying their own upload.
     *
     * MessageDigest is not thread-safe, so a fresh instance is obtained per
     * call rather than cached in a field - the same hazard TokenHasher
     * documents for Mac.
     */
    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every conforming JVM. If this
            // throws, the platform is broken in a way no fallback would fix.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
