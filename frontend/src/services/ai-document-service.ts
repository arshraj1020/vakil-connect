import api from "@/lib/axios";
import type {
  AskQuestionRequest,
  DocumentAnalysis,
  DocumentResponse,
  DocumentSummaryResponse,
  DocumentUploadResponse,
  IngestionResult,
  RagAnswer,
  Uuid,
} from "@/types";

/**
 * Document intelligence endpoints (AI-1 through AI-4).
 *
 * Open to any authenticated role - the backend does not scope these under
 * `/api/client/**` or `/api/lawyer/**`, because ownership rather than role is
 * the boundary that matters: a lawyer reviewing a contract and a client
 * uploading one want the same feature. Identity comes entirely from the JWT;
 * no endpoint here accepts a user id.
 *
 * ANOTHER USER'S DOCUMENT ID ALWAYS RETURNS 404, NOT 403. Callers should not
 * distinguish "not found" from "not yours" - the backend deliberately does
 * not either, so as not to disclose that a document exists.
 */

const ENDPOINTS = {
  documents: "/api/ai/documents",
  document: (documentId: Uuid) => `/api/ai/documents/${documentId}`,
  process: (documentId: Uuid) => `/api/ai/documents/${documentId}/process`,
  analyze: (documentId: Uuid) => `/api/ai/documents/${documentId}/analyze`,
  ask: "/api/ai/documents/ask",
} as const;

/**
 * Uploads one document. Multipart, not JSON - the backend takes a single
 * `file` part with no accompanying body.
 *
 * Lands PENDING. Nothing is extracted or indexed until {@link processDocument}
 * is called.
 */
export async function uploadDocument(
  file: File,
): Promise<DocumentUploadResponse> {
  const form = new FormData();
  form.append("file", file);

  /*
   * Content-Type IS DELIBERATELY NOT SET HERE. The instance default is
   * application/json (see lib/axios.ts); sending FormData with that header
   * explicitly overridden to "multipart/form-data" would omit the boundary
   * parameter the browser computes automatically, and the backend's
   * multipart parser would then fail to find any part at all. Setting it to
   * `undefined` removes the default for this one request and lets the
   * browser compute the full header, boundary included.
   */
  const { data } = await api.post<DocumentUploadResponse>(
    ENDPOINTS.documents,
    form,
    { headers: { "Content-Type": undefined } },
  );
  return data;
}

/** The caller's own documents, newest first. Unpaged. */
export async function listDocuments(): Promise<DocumentSummaryResponse[]> {
  const { data } = await api.get<DocumentSummaryResponse[]>(
    ENDPOINTS.documents,
  );
  return data;
}

/** Full metadata for one of the caller's own documents. */
export async function getDocument(
  documentId: Uuid,
): Promise<DocumentResponse> {
  const { data } = await api.get<DocumentResponse>(
    ENDPOINTS.document(documentId),
  );
  return data;
}

/** Deletes one of the caller's own documents. */
export async function deleteDocument(documentId: Uuid): Promise<void> {
  await api.delete(ENDPOINTS.document(documentId));
}

/**
 * Extracts, chunks and embeds one document. Synchronous - the response
 * arrives when indexing is done, which against a local model can take tens of
 * seconds on a large file.
 *
 * Safe to call again on a READY document: reprocessing REPLACES the chunks
 * rather than duplicating them.
 */
export async function processDocument(
  documentId: Uuid,
): Promise<IngestionResult> {
  const { data } = await api.post<IngestionResult>(
    ENDPOINTS.process(documentId),
  );
  return data;
}

/**
 * Structured analysis of one document (AI-4). Requires the document to be
 * READY - a PENDING or FAILED document answers 409 with a code the caller
 * should surface as "process it first", not as a generic error.
 */
export async function analyzeDocument(
  documentId: Uuid,
): Promise<DocumentAnalysis> {
  const { data } = await api.post<DocumentAnalysis>(
    ENDPOINTS.analyze(documentId),
  );
  return data;
}

/**
 * Grounded Q&A over every document the caller owns (AI-3). There is no
 * document id in the request - the search is corpus-wide, because a user
 * asking a question does not necessarily know which upload answers it.
 *
 * A 200 with `grounded: false` is the normal "not found in your documents"
 * outcome, not a failure - see {@link RagAnswer}.
 */
export async function askQuestion(question: string): Promise<RagAnswer> {
  const payload: AskQuestionRequest = { question };
  const { data } = await api.post<RagAnswer>(ENDPOINTS.ask, payload);
  return data;
}

export const aiDocumentService = {
  uploadDocument,
  listDocuments,
  getDocument,
  deleteDocument,
  processDocument,
  analyzeDocument,
  askQuestion,
} as const;
