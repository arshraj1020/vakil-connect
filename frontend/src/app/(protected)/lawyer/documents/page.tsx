import type { Metadata } from "next";

import { DocumentsView } from "@/features/ai-documents/components/documents-view";

export const metadata: Metadata = {
  title: "Documents",
  description: "Upload, index and ask questions about your legal documents.",
};

/**
 * Identical to the client section's documents page (`(protected)/client/documents`) -
 * both render `DocumentsView`, because the backend does not scope `/api/ai/documents/**`
 * by role. Ownership, not role, is the boundary here.
 */
export default function LawyerDocumentsPage() {
  return <DocumentsView />;
}
