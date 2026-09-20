import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { getDocumentStatusMeta } from "@/lib/document-status";
import type { DocumentStatus } from "@/types";

/** Renders a document's ingestion status. Mirrors `StatusBadge` exactly. */
export function DocumentStatusBadge({
  status,
  className,
}: {
  status: DocumentStatus;
  className?: string;
}) {
  const { label, intent, icon: Icon } = getDocumentStatusMeta(status);

  return (
    <Badge variant={intent} className={cn(className)}>
      <Icon aria-hidden className={status === "PROCESSING" ? "animate-spin" : undefined} />
      {label}
    </Badge>
  );
}
