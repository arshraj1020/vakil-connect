"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/lib/query-keys";
import { aiDocumentService } from "@/services/ai-document-service";

/** Uploads a document, then refreshes the list so the new PENDING row appears. */
export function useUploadDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (file: File) => aiDocumentService.uploadDocument(file),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: queryKeys.aiDocuments.list(),
      });
    },
  });
}
