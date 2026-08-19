package com.arshraj.vakilconnect.ai.document.controller;

import com.arshraj.vakilconnect.ai.document.dto.DocumentResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentSummaryResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentUploadResponse;
import com.arshraj.vakilconnect.ai.document.service.AiDocumentService;
import com.arshraj.vakilconnect.ai.ingest.DocumentIngestionService;
import com.arshraj.vakilconnect.ai.ingest.IngestionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Document upload and management for the authenticated user.
 *
 * SECURED BY SecurityConfig's DEFAULT-DENY, WITH NO NEW MATCHER.
 * `anyRequest().authenticated()` already covers `/api/ai/**`, so an anonymous
 * request is rejected by the filter chain before this class is reached. Adding
 * an explicit rule would restate what default-deny already guarantees, and
 * every rule added to that chain is another line whose ordering has to be got
 * right. SecurityConfig is therefore UNCHANGED by AI-1.
 *
 * DELIBERATELY NOT ROLE-SCOPED. The existing chain gates `/api/client/**`,
 * `/api/lawyer/**` and `/api/admin/**` by role; these routes sit under
 * `/api/ai/**` and are open to any authenticated account. That is the intended
 * design - a lawyer reviewing a contract and a client uploading one want the
 * same feature - and the security boundary that matters here is OWNERSHIP, not
 * role. Ownership is enforced in SQL, in every query, in the service layer.
 *
 * THE CALLER IS IDENTIFIED FROM THE SECURITY CONTEXT ONLY. Every method passes
 * {@code authentication.getName()} - populated by JwtAuthenticationFilter from
 * a signature-verified token - and there is no user id in any path, body or
 * header. The frontend cannot assert who it is, so it cannot lie about it.
 *
 * ENTITIES NEVER CROSS THIS BOUNDARY. Every return type is a DTO record, and
 * none of them carries the document's bytes. AI-1 exposes NO endpoint that
 * returns file content at all.
 */
@RestController
@RequestMapping("/api/ai/documents")
public class AiDocumentController {

    private final AiDocumentService documentService;
    private final DocumentIngestionService ingestionService;

    public AiDocumentController(AiDocumentService documentService,
                                DocumentIngestionService ingestionService) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
    }

    /**
     * Uploads one document. 201 with its metadata.
     *
     * MULTIPART, NOT A BASE64 JSON BODY. Base64 inflates the payload by a third
     * and forces the whole thing through the JSON parser into a String before
     * anything can check its size - so the size limit would be enforced after
     * the memory had already been spent.
     *
     * There is no request DTO, and that is not an omission: the request is a
     * single `file` part with no accompanying JSON, so a {@code @RequestBody}
     * record would have no fields and no purpose. What a request DTO would
     * normally carry - the field rules - lives in DocumentFilenameSanitizer and
     * DocumentContentTypeDetector, because these rules are about BYTES rather
     * than about a deserialised shape, and no bean-validation annotation can
     * express "the magic number must agree with the extension".
     *
     * {@code required = false} on the part, deliberately. The default makes
     * Spring throw MissingServletRequestPartException, a 500 through this
     * project's handler chain, before the service can say anything useful.
     * Accepting null and letting the service raise DOCUMENT_EMPTY gives the
     * client an error code it can act on.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            Authentication authentication,
            @RequestParam(name = "file", required = false) MultipartFile file) {

        DocumentUploadResponse response =
                documentService.upload(authentication.getName(), file);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** The caller's own documents, newest first. */
    @GetMapping
    public List<DocumentSummaryResponse> list(Authentication authentication) {
        return documentService.listOwnDocuments(authentication.getName());
    }

    /**
     * Metadata for one of the caller's own documents.
     *
     * Another user's id yields 404, not 403 - see AiDocumentServiceImpl for
     * why. A non-UUID path variable is a 400 via the existing
     * MethodArgumentTypeMismatchException handler, so a malformed id never
     * reaches the service.
     */
    @GetMapping("/{documentId}")
    public DocumentResponse get(Authentication authentication,
                                @PathVariable UUID documentId) {

        return documentService.getOwnDocument(authentication.getName(), documentId);
    }

    /**
     * Deletes one of the caller's own documents. 204.
     *
     * NOT IDEMPOTENT-BY-SILENCE: deleting something that is not there returns
     * 404 rather than a cheerful 204. The strict reading of HTTP would allow
     * either, but a silent success here would mean a client deleting the wrong
     * id - or another user's - gets the same answer as one that worked, and
     * that is exactly the feedback a caller needs.
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(Authentication authentication,
                                       @PathVariable UUID documentId) {

        documentService.deleteOwnDocument(authentication.getName(), documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extracts, chunks and embeds one of the caller's own documents (AI-2).
     *
     * SECURITY: identical to every other route here. Authenticated by
     * SecurityConfig's default-deny; the caller comes from the security context
     * only; another user's id yields 404, not 403, so the endpoint is not an
     * oracle for what other users hold.
     *
     * STATES: allowed from PENDING, FAILED and READY. READY is deliberate -
     * reprocessing after a chunk-size or model change is a legitimate
     * operation, and it is safe because stage 3 REPLACES chunks rather than
     * appending. PROCESSING is refused with 409, which is the conditional
     * UPDATE's row count surfacing rather than a check-then-act race.
     *
     * IDEMPOTENT: running it twice on an unchanged document produces
     * byte-identical chunks in the same order, because extraction,
     * normalization and chunking are all deterministic.
     *
     * SYNCHRONOUS, so the response arrives when indexing is done. Bounded by
     * AI-1's 10MB upload cap; a large document against a local CPU model can
     * take tens of seconds, which is the accepted cost of not introducing an
     * executor, duplicate-run control and a stuck-state sweeper in the same
     * phase that introduces the pipeline.
     *
     * 200, NOT 201. Nothing new is addressable afterwards - the document
     * already existed and its URI is unchanged. The chunks are internal to
     * retrieval and have no public URI in AI-2.
     *
     * RETURNS COUNTS AND STATE ONLY. No chunk text, no preview, no embeddings.
     * NOT A Q&A ENDPOINT - AI-2 has no retrieval and no chat.
     */
    @PostMapping("/{documentId}/process")
    public IngestionResult process(Authentication authentication,
                                   @PathVariable UUID documentId) {

        return ingestionService.process(authentication.getName(), documentId);
    }
}
