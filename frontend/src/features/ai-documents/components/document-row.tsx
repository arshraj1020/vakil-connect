"use client";

import { FileText, Sparkles, Trash2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { ErrorState } from "@/components/common/error-state";
import { Spinner } from "@/components/ui/spinner";
import { DocumentAnalysisPanel } from "@/features/ai-documents/components/document-analysis-panel";
import { DocumentStatusBadge } from "@/features/ai-documents/components/document-status-badge";
import { useAnalyzeDocument } from "@/features/ai-documents/hooks/use-analyze-document";
import { useDeleteDocument } from "@/features/ai-documents/hooks/use-delete-document";
import { useProcessDocument } from "@/features/ai-documents/hooks/use-process-document";
import { formatTimestamp } from "@/lib/date";
import { formatFileSize } from "@/lib/document-status";
import { isApiError, type DocumentSummaryResponse } from "@/types";

/**
 * One document: metadata, status, and the actions valid for its current
 * state.
 *
 * ACTIONS ARE GATED BY STATUS, MATCHING THE BACKEND'S OWN RULES:
 *   PENDING / FAILED  -> "Process" is the only useful action
 *   PROCESSING        -> nothing; a request mid-flight cannot be re-processed
 *   READY             -> "Analyze" becomes available; "Process" stays offered
 *                        for re-indexing after an edit elsewhere
 *
 * A 409 from `/analyze` on an unprocessed document is handled here rather than
 * hidden behind disabling the button before the fact: the list can be briefly
 * stale (e.g. two tabs open), and surfacing the backend's real answer is more
 * honest than guessing client-side.
 */
export function DocumentRow({ document }: { document: DocumentSummaryResponse }) {
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [analysisOpen, setAnalysisOpen] = useState(false);

  const process = useProcessDocument();
  const analyze = useAnalyzeDocument();
  const deleteDocument = useDeleteDocument();

  function handleProcess() {
    process.mutate(document.id, {
      onSuccess: (result) => {
        toast.success("Document processed", {
          description: `${result.chunkCount} chunk(s) indexed.`,
        });
      },
      onError: (error: unknown) => {
        toast.error("Processing failed", {
          description: isApiError(error)
            ? error.message
            : "This document could not be processed.",
        });
      },
    });
  }

  function handleAnalyze() {
    setAnalysisOpen(true);
    analyze.mutate(document.id, {
      onError: (error: unknown) => {
        if (isApiError(error) && error.code === "DOCUMENT_NOT_ANALYZABLE") {
          toast.error("Not processed yet", {
            description: "Process this document before requesting an analysis.",
          });
          return;
        }
        toast.error("Analysis failed", {
          description: isApiError(error)
            ? error.message
            : "The analysis could not be completed.",
        });
      },
    });
  }

  function handleDelete() {
    deleteDocument.mutate(document.id, {
      onSuccess: () => {
        toast.success("Document deleted");
        setConfirmingDelete(false);
      },
      onError: (error: unknown) => {
        toast.error("Could not delete", {
          description: isApiError(error) ? error.message : undefined,
        });
      },
    });
  }

  const canProcess = document.status === "PENDING" || document.status === "FAILED";
  const canAnalyze = document.status === "READY";

  return (
    <Card>
      <CardContent className="space-y-4 py-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
              <FileText className="size-4" aria-hidden />
            </span>
            <div className="min-w-0 space-y-0.5">
              <p className="truncate font-medium" title={document.filename}>
                {document.filename}
              </p>
              <p className="text-xs text-muted-foreground">
                {formatFileSize(document.sizeBytes)} · Uploaded{" "}
                {formatTimestamp(document.createdAt)}
              </p>
            </div>
          </div>

          <DocumentStatusBadge status={document.status} />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {canProcess ? (
            <Button size="sm" variant="outline" onClick={handleProcess} disabled={process.isPending}>
              {process.isPending ? <Spinner size="sm" /> : null}
              {document.status === "FAILED" ? "Retry processing" : "Process"}
            </Button>
          ) : null}

          {canAnalyze ? (
            <Button size="sm" variant="outline" onClick={handleAnalyze}>
              <Sparkles aria-hidden />
              Analyze
            </Button>
          ) : null}

          <Button
            size="sm"
            variant="ghost"
            className="ml-auto text-destructive hover:text-destructive"
            onClick={() => setConfirmingDelete(true)}
          >
            <Trash2 aria-hidden />
            Delete
          </Button>
        </div>

        {analysisOpen ? (
          <div className="rounded-lg border border-border bg-muted/30 p-4">
            {analyze.isPending ? (
              <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
                <Spinner size="sm" />
                Analyzing - this can take a little while on a local model.
              </div>
            ) : analyze.isError ? (
              <ErrorState error={analyze.error} onRetry={handleAnalyze} />
            ) : analyze.data ? (
              <DocumentAnalysisPanel analysis={analyze.data} />
            ) : null}
          </div>
        ) : null}
      </CardContent>

      <ConfirmDialog
        open={confirmingDelete}
        onOpenChange={setConfirmingDelete}
        title="Delete this document?"
        description={`"${document.filename}" and its indexed content will be permanently removed.`}
        confirmLabel="Delete"
        destructive
        isPending={deleteDocument.isPending}
        onConfirm={handleDelete}
      />
    </Card>
  );
}
