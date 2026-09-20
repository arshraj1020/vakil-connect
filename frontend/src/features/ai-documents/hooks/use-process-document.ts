"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { aiDocumentService } from "@/services/ai-document-service";
import type { Uuid } from "@/types";

/**
 * Runs AI-2's extraction/chunking/embedding pipeline on one document.
 *
 * SYNCHRONOUS ON THE BACKEND: against a local CPU model this can take tens of
 * seconds on a large file, so the calling component should show its own
 * pending state (`isPending`) rather than relying on the list's poll to
 * reflect PROCESSING - the request may well resolve to READY before the next
 * poll tick fires.
 */
export function useProcessDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (documentId: Uuid) =>
      aiDocumentService.processDocument(documentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: queryKeys.aiDocuments.list(),
      });
    },
  });
}
