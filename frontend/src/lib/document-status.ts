import {
  AlertTriangle,
  CheckCircle2,
  Loader2,
  type LucideIcon,
  UploadCloud,
} from "lucide-react";

import type { DocumentStatus } from "@/types";

import type { StatusIntent } from "./status";

/**
 * Semantic presentation for AI-2's document ingestion lifecycle.
 *
 * Mirrors `APPOINTMENT_STATUS_META`'s shape exactly - one place a status
 * becomes a label, a colour intent and an icon, and an exhaustive `Record` so
 * a future backend status fails this file to compile rather than rendering
 * unstyled.
 */
export interface DocumentStatusMeta {
  label: string;
  intent: StatusIntent;
  icon: LucideIcon;
  description: string;
}

export const DOCUMENT_STATUS_META: Record<DocumentStatus, DocumentStatusMeta> = {
  PENDING: {
    label: "Not processed",
    intent: "secondary",
    icon: UploadCloud,
    description: "Uploaded, but not yet indexed. Process it to ask questions or analyze it.",
  },
  PROCESSING: {
    label: "Processing",
    intent: "info",
    icon: Loader2,
    description: "Extracting text and indexing this document.",
  },
  READY: {
    label: "Ready",
    intent: "success",
    icon: CheckCircle2,
    description: "Indexed. You can ask questions or request an analysis.",
  },
  FAILED: {
    label: "Failed",
    intent: "destructive",
    icon: AlertTriangle,
    description: "This document could not be processed.",
  },
};

export function getDocumentStatusMeta(status: DocumentStatus): DocumentStatusMeta {
  return DOCUMENT_STATUS_META[status];
}

/** "128 KB" / "3.4 MB" - matches the units a user picked the file with. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
