"use client";

import { useMutation } from "@tanstack/react-query";

import { aiDocumentService } from "@/services/ai-document-service";
import type { Uuid } from "@/types";

/**
 * Requests a structured analysis of one document (AI-4).
 *
 * A MUTATION, NOT A QUERY, even though nothing on the server is written.
 * Generation is not idempotent - re-running this can legitimately produce
 * different prose from the same document - so caching the result under a
 * query key would risk a component reading a stale analysis that looks
 * current. Each click is therefore a fresh call, and the component holds the
 * latest result in the mutation's own `data`.
 *
 * 409 means the document has not been processed yet - the component should
 * check `error.code === "DOCUMENT_NOT_ANALYZABLE"` and offer to process it,
 * rather than showing a generic failure.
 */
export function useAnalyzeDocument() {
  return useMutation({
    mutationFn: (documentId: Uuid) =>
      aiDocumentService.analyzeDocument(documentId),
  });
}
