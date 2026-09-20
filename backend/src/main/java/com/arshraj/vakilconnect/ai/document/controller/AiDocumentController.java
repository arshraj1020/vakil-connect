package com.arshraj.vakilconnect.ai.document.controller;

import com.arshraj.vakilconnect.ai.analysis.DocumentAnalysis;
import com.arshraj.vakilconnect.ai.analysis.DocumentAnalysisService;
import com.arshraj.vakilconnect.ai.compare.DocumentComparison;
import com.arshraj.vakilconnect.ai.compare.DocumentComparisonRequest;
import com.arshraj.vakilconnect.ai.compare.DocumentComparisonService;
import com.arshraj.vakilconnect.ai.document.dto.DocumentResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentSummaryResponse;
import com.arshraj.vakilconnect.ai.document.dto.DocumentUploadResponse;
import com.arshraj.vakilconnect.ai.document.service.AiDocumentService;
import com.arshraj.vakilconnect.ai.ingest.DocumentIngestionService;
import com.arshraj.vakilconnect.ai.ingest.IngestionResult;
import com.arshraj.vakilconnect.ai.rag.AskQuestionRequest;
import com.arshraj.vakilconnect.ai.rag.RagAnswer;
import com.arshraj.vakilconnect.ai.rag.RagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final RagService ragService;
    private final DocumentAnalysisService analysisService;
    private final DocumentComparisonService comparisonService;

    public AiDocumentController(AiDocumentService documentService,
                                DocumentIngestionService ingestionService,
                                RagService ragService,
                                DocumentAnalysisService analysisService,
                                DocumentComparisonService comparisonService) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
        this.ragService = ragService;
        this.analysisService = analysisService;
        this.comparisonService = comparisonService;
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

    /**
     * Answers a question from the caller's own documents (AI-3).
     *
     * ONE ENDPOINT, NOT TWO. A per-document variant
     * ({@code /{id}/ask}) was considered and rejected: it is a filter on the
     * retrieval query, not a different capability, and shipping both would mean
     * two paths to maintain and two places to get the ownership predicate right.
     * The corpus-wide question is also the more useful one - a user asking "what
     * is my notice period" does not necessarily know which upload contains the
     * answer. Narrowing to one document becomes an optional field on this
     * request when something actually needs it.
     *
     * NO DOCUMENT ID IN THE REQUEST AT ALL. The search is scoped to whatever the
     * authenticated user owns, and the owner comes from the security context.
     * An id in the payload would be a client-supplied identifier feeding a
     * retrieval query - re-verified anyway, so it would add attack surface and
     * no capability.
     *
     * SECURITY: authenticated by SecurityConfig's default-deny, like every route
     * here. Ownership is enforced inside the vector search's WHERE clause, so
     * another user's chunks are never scored or returned. Cross-user access
     * needs no 404 path on this endpoint because there is no id to probe - the
     * question simply searches an empty corpus and gets insufficient evidence.
     *
     * 200 EVEN WHEN THE DOCUMENTS DO NOT ANSWER. `grounded: false` is a
     * successful, honest response, not an error. A 404 there would make a
     * working system look broken every time somebody asked about something they
     * had not uploaded.
     *
     * RETURNS AN ANSWER AND STRUCTURED SOURCES. No embeddings, no entities, no
     * chunk ids beyond what a citation needs.
     */
    @PostMapping("/ask")
    public RagAnswer ask(Authentication authentication,
                         @Valid @RequestBody AskQuestionRequest request) {

        return ragService.ask(authentication.getName(), request.question());
    }

    /**
     * Structured analysis of ONE of the caller's own documents (AI-4).
     *
     * PER-DOCUMENT, WHERE /ask IS CORPUS-WIDE, and the difference is real rather
     * than cosmetic. A question searches everything the user owns because they
     * may not know which upload answers it; an analysis is a statement ABOUT a
     * particular document, so the document is the resource and its id belongs in
     * the path. That also means this route has a cross-user case to get right,
     * which /ask does not.
     *
     * SECURITY: authenticated by SecurityConfig's default-deny, like every route
     * here. The owner comes from the security context only - the path carries a
     * document id and nothing else, so there is no user id for a client to
     * assert. Ownership is enforced in the WHERE clause of the metadata read,
     * BEFORE any document text is loaded, so another user's content is never
     * read out of the database at all.
     *
     * ANOTHER USER'S ID RETURNS 404, NOT 403, matching every other route on this
     * controller. A 403 would confirm the document exists.
     *
     * NO REQUEST BODY. There is nothing for the caller to supply: the document
     * is named by the path and the owner by the token. A body would only add
     * fields that the service would have to re-verify or ignore.
     *
     * 200, NOT 201. Nothing is created and nothing new is addressable
     * afterwards; the analysis is derived, not stored. Running it twice is
     * therefore safe, and will produce two possibly-different analyses of the
     * same text - generation is not deterministic, and this endpoint does not
     * pretend otherwise by caching one.
     *
     * SYNCHRONOUS, so the response arrives when the model has finished. Against
     * a local CPU model that is tens of seconds, which is the accepted cost of
     * not introducing an executor and a job table in a phase that is meant to be
     * small.
     *
     * 409 WHEN THE DOCUMENT HAS NOT BEEN PROCESSED. Analysis reads AI-2's
     * chunks; a PENDING document has none, and the remedy is to call
     * {@code /process} rather than to retry this.
     *
     * RETURNS STRUCTURED FIELDS, NEVER A RAW MODEL BLOB. The model's reply is
     * parsed into six named content fields; {@code documentId} and
     * {@code documentName} come from the database row, so nothing the model
     * writes can change which document the response claims to describe.
     */
    @PostMapping("/{documentId}/analyze")
    public DocumentAnalysis analyze(Authentication authentication,
                                    @PathVariable UUID documentId) {

        return analysisService.analyze(authentication.getName(), documentId);
    }

    /**
     * Compares two of the caller's own documents (AI-5).
     *
     * A LITERAL SEGMENT, `/compare`, NOT A PATH VARIABLE - deliberately, so it
     * cannot be confused with `/{documentId}` at the routing layer, and so a
     * client reading this API can tell at a glance that two ids are required
     * rather than one. Spring's path matching already disambiguates a literal
     * segment from a variable one at the same position, the same way `/ask`
     * coexists with `GET /{documentId}` today.
     *
     * TWO IDS IN THE BODY, UNLIKE /ask AND /analyze. Neither of those routes
     * needs a client-supplied id - /ask searches everything the caller owns,
     * /analyze names its one document in the path - but a comparison
     * inherently names two specific resources, so they belong in the request.
     * Both are RE-VERIFIED against the caller's ownership inside the service
     * regardless of what is supplied here, exactly like every id anywhere in
     * this controller; the body is a selection, never an authorization.
     *
     * SECURITY: authenticated by SecurityConfig's default-deny, like every
     * route here. EITHER id belonging to somebody else returns 404, not 403 -
     * same anti-enumeration convention - and BOTH documents are loaded before
     * either failure is reported, so pairing an owned id with an unowned one
     * costs and reveals the same as pairing two unowned ones.
     *
     * 200, NOT 201, for the same reason /analyze is: nothing is created, the
     * comparison is derived and not stored, and running it twice may
     * legitimately produce different prose from the same two documents.
     *
     * 400 WHEN THE SAME ID IS GIVEN TWICE. A document cannot be meaningfully
     * compared with itself; the service refuses before either lookup runs.
     *
     * 409 WHEN EITHER DOCUMENT HAS NOT BEEN PROCESSED - identical remedy to
     * /analyze: call {@code /process} on the unprocessed one first.
     *
     * RETURNS STRUCTURED FIELDS, NEVER A RAW MODEL BLOB. Both documents'
     * identity comes from the database rows loaded before the model was
     * called; the model contributes only the four content fields the parser
     * reads by name.
     */
    @PostMapping("/compare")
    public DocumentComparison compare(Authentication authentication,
                                      @Valid @RequestBody DocumentComparisonRequest request) {

        return comparisonService.compare(authentication.getName(),
                request.documentId(), request.compareToDocumentId());
    }
}
