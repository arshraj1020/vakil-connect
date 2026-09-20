"use client";

import { useMutation } from "@tanstack/react-query";

import { aiDocumentService } from "@/services/ai-document-service";

/**
 * Grounded Q&A over the caller's documents (AI-3).
 *
 * A mutation for the same reason `useAnalyzeDocument` is: an answer is a
 * generated artifact of a moment in time, not a cacheable resource, and the
 * question text itself is not something this app should ever persist in a
 * query key (it can be an arbitrarily personal string about someone's legal
 * situation).
 */
export function useAskQuestion() {
  return useMutation({
    mutationFn: (question: string) => aiDocumentService.askQuestion(question),
  });
}
