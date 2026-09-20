import type { IsoDateTime, Uuid } from "./common";

/**
 * Document intelligence DTOs (AI-1 through AI-4).
 *
 * Mirrors the frozen backend contract exactly, the same rule every other file
 * in this directory follows. Nothing here is inferred from usage - each shape
 * is read straight off the Java record it represents.
 */

/** Where a document sits in AI-2's ingestion lifecycle. */
export const DOCUMENT_STATUS = {
  PENDING: "PENDING",
  PROCESSING: "PROCESSING",
  READY: "READY",
  FAILED: "FAILED",
} as const;

export type DocumentStatus =
  (typeof DOCUMENT_STATUS)[keyof typeof DOCUMENT_STATUS];

/** AI-1: `POST /api/ai/documents` response. */
export interface DocumentUploadResponse {
  id: Uuid;
  filename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  status: DocumentStatus;
  createdAt: IsoDateTime;
}

/** AI-1: one row of `GET /api/ai/documents`. */
export interface DocumentSummaryResponse {
  id: Uuid;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  createdAt: IsoDateTime;
}

/** AI-1: `GET /api/ai/documents/{id}`. */
export interface DocumentResponse {
  id: Uuid;
  filename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  status: DocumentStatus;
  /** Present only when status is FAILED - the backend omits the key otherwise. */
  failureReason?: string;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

/** AI-2: `POST /api/ai/documents/{id}/process` response. */
export interface IngestionResult {
  documentId: Uuid;
  status: DocumentStatus;
  chunkCount: number;
  totalCharacters: number;
  /** The embedding model that indexed this document, e.g. "nomic-embed-text". */
  model: string;
  dimension: number;
}

/** AI-3: `POST /api/ai/documents/ask` request body. */
export interface AskQuestionRequest {
  question: string;
}

/**
 * AI-3: one citation backing a grounded answer.
 *
 * `excerpt` is a short, truncated preview - never the whole chunk - so this is
 * safe to render directly without a second fetch.
 */
export interface RagSource {
  documentId: Uuid;
  documentName: string;
  chunkIndex: number;
  excerpt: string;
}

/**
 * AI-3: `POST /api/ai/documents/ask` response.
 *
 * `grounded: false` is a SUCCESSFUL response, not an error - it means
 * retrieval found nothing relevant enough, and `sources` is empty. Render it
 * as an honest "not found in your documents" state, not as a failure.
 */
export interface RagAnswer {
  answer: string;
  grounded: boolean;
  sources: RagSource[];
  /** True when the character budget dropped some retrieved evidence. */
  truncated: boolean;
}

/**
 * AI-4: `POST /api/ai/documents/{id}/analyze` response.
 *
 * `documentId` and `documentName` are echoed from the database, never from the
 * model - there is nothing to distrust about them. Every list is possibly
 * empty; an empty array means the document states none of that category, which
 * is itself a finding.
 */
export interface DocumentAnalysis {
  documentId: Uuid;
  documentName: string;
  summary: string;
  parties: string[];
  importantDates: string[];
  obligations: string[];
  keyClauses: string[];
  risks: string[];
  /** True when the document was larger than the analysis context budget. */
  truncated: boolean;
}
