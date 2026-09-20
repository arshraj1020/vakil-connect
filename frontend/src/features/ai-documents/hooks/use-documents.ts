"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { aiDocumentService } from "@/services/ai-document-service";

/**
 * The signed-in user's own uploaded documents, newest first.
 *
 * `refetchInterval` is short and conditional: while ANY document is still
 * PENDING or PROCESSING, the list is polled every 3s so a status change (from
 * an upload elsewhere, or from this tab's own processing call resolving)
 * appears without a manual refresh. Once every document has settled into
 * READY or FAILED, polling stops - there is nothing left that could change on
 * its own.
 */
export function useDocuments() {
  const query = useQuery({
    queryKey: queryKeys.aiDocuments.list(),
    queryFn: aiDocumentService.listDocuments,
    refetchInterval: (result) => {
      const documents = result.state.data ?? [];
      const settling = documents.some(
        (document) =>
          document.status === "PENDING" || document.status === "PROCESSING",
      );
      return settling ? 3_000 : false;
    },
  });

  return { ...query, documents: query.data ?? [] };
}
