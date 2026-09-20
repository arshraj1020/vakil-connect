"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { aiDocumentService } from "@/services/ai-document-service";
import type { Uuid } from "@/types";

export function useDeleteDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (documentId: Uuid) =>
      aiDocumentService.deleteDocument(documentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: queryKeys.aiDocuments.list(),
      });
    },
  });
}
