"use client";

import { FileStack } from "lucide-react";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { ListSkeleton } from "@/components/common/loading-skeleton";
import { PageHeader } from "@/components/common/page-header";
import { DocumentAskPanel } from "@/features/ai-documents/components/document-ask-panel";
import { DocumentRow } from "@/features/ai-documents/components/document-row";
import { DocumentUploadCard } from "@/features/ai-documents/components/document-upload-card";
import { useDocuments } from "@/features/ai-documents/hooks/use-documents";

/**
 * The document intelligence workspace: upload, per-document status and
 * actions, and corpus-wide Q&A - AI-1 through AI-4 in one screen.
 *
 * Shared verbatim between the client and lawyer sections rather than
 * duplicated: the backend does not scope these endpoints by role, and neither
 * does this view. The two thin page files under `/client/documents` and
 * `/lawyer/documents` exist only to sit inside each role's `<RoleGuard>` and
 * navigation.
 */
export function DocumentsView() {
  const { documents, isLoading, isError, error, refetch } = useDocuments();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Documents"
        description="Upload a document to index it, ask questions across everything you've uploaded, or request a structured analysis of any one file."
      />

      <DocumentUploadCard />

      <DocumentAskPanel />

      <div className="space-y-3">
        {isLoading ? (
          <ListSkeleton count={3} />
        ) : isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : documents.length === 0 ? (
          <EmptyState
            icon={FileStack}
            title="No documents yet"
            description="Upload a lease, contract or agreement above to get started."
          />
        ) : (
          documents.map((document) => (
            <DocumentRow key={document.id} document={document} />
          ))
        )}
      </div>
    </div>
  );
}
